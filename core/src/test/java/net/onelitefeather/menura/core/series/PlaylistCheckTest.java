package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.post.ExternalPostFormat;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.DirectTransactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.series.SeriesAssignment.PlaylistState;
import net.onelitefeather.menura.core.series.SeriesPlaylistCheck.Report;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.core.series.support.PlaylistPlatform;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The playlist check of series posts, after the platform data was collected. The clock is injected and never waits. */
class PlaylistCheckTest {

    private static final UUID EDITOR = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");

    @Test
    void aSeriesPostMissingFromItsPlaylistAfter24HoursIsFlagged() throws SQLException {
        Setup setup = new Setup(new PlaylistPlatform(true).playlist("PL1", "someone-else"), "v1",
                Duration.ofHours(25));

        Report report = setup.run();

        assertEquals(PlaylistState.MISSING, setup.state(), "a post 25 hours old and missing from the playlist is flagged");
        assertEquals(1, report.missing(), "the report counts one missing post");
    }

    @Test
    void aSeriesPostInItsPlaylistIsNotFlagged() throws SQLException {
        Setup setup = new Setup(new PlaylistPlatform(true).playlist("PL1", "v1"), "v1", Duration.ofHours(25));

        setup.run();

        assertEquals(PlaylistState.IN_PLAYLIST, setup.state(), "a post present in its playlist carries no flag");
    }

    @Test
    void aPostLessThan24HoursOldIsNotCheckedYet() throws SQLException {
        Setup setup = new Setup(new PlaylistPlatform(true).playlist("PL1"), "v1", Duration.ofHours(23));

        Report report = setup.run();

        assertNull(setup.state(), "a post 23 hours old is not checked yet");
        assertEquals(0, report.checked(), "no post is due");
    }

    @Test
    void anAdapterThatCannotReadPlaylistsShowsNotCheckedAndSetsNoFlag() throws SQLException {
        Setup setup = new Setup(new PlaylistPlatform(false), "v1", Duration.ofHours(25));

        Report report = setup.run();

        assertEquals(PlaylistState.NOT_CHECKED, setup.state(), "the post shows that the playlist is not checked");
        assertEquals(0, report.missing(), "no flag is set for a platform that does not report playlists");
        assertEquals(1, report.notChecked(), "the report counts the post as not checked");
    }

    /** One channel, one series with a playlist on it, and one post assigned to that series. */
    private static final class Setup {

        final SeriesFixture world = new SeriesFixture();
        final UUID channel = world.channel("UC1");
        final UUID post;
        final SeriesPlaylistCheck check;

        Setup(PlaylistPlatform platform, String itemId, Duration age) throws SQLException {
            world.channels.saveGrant(null, new ChannelGrant(channel, "test-token", null,
                    SeriesFixture.NOW.plus(Duration.ofDays(365)), SeriesFixture.NOW));
            ContentSeries series = world.service.create(null,
                    SeriesFixture.draftWithPlaylist("Serie", "#serie", channel, "PL1"));
            ExternalPost stored = SeriesFixture.post(channel, itemId, ExternalPostFormat.SHORT, SeriesFixture.NOW.minus(age));
            world.posts.insert(null, stored);
            world.service.assign(null, stored.id(), new AssignmentCommand(series.id(), false, false), EDITOR);
            this.post = stored.id();
            PlatformRegistry registry = new PlatformRegistry(List.of(platform));
            ConnectionService connections = new ChannelFixture().service(world.channels, registry);
            this.check = new SeriesPlaylistCheck(new DirectTransactions(), world.clock, world.series, world.posts,
                    world.channels, connections, registry);
        }

        Report run() throws SQLException {
            return check.run();
        }

        PlaylistState state() throws SQLException {
            return world.series.findAssignment(null, post).orElseThrow().playlistState();
        }
    }
}
