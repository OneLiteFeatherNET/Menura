package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.post.ExternalPostFormat;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.core.series.SeriesReports.Episode;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Episode numbers: sequential per series, kept with the post, and never given twice. */
class EpisodeNumberTest {

    private static final UUID EDITOR = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");

    @Test
    void aNewPostTakesTheNextNumberOfItsSeries() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, SeriesFixture.draft("Serie", "#serie"));
        UUID channel = world.channel("UC1");
        for (int i = 1; i <= 4; i++) {
            assignNew(world, channel, "v" + i, series.id());
        }

        assignNew(world, channel, "v5", series.id());

        assertEquals(5, numberOf(world, series.id(), "v5"), "the fifth post receives episode number 5");
    }

    @Test
    void aPostThatComesBackToItsSeriesKeepsItsNumberAndNoOtherPostGetsIt() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries first = world.service.create(null, SeriesFixture.draft("Erste", "#erste"));
        ContentSeries second = world.service.create(null, SeriesFixture.draft("Zweite", "#zweite"));
        UUID channel = world.channel("UC1");
        ExternalPost post = assignNew(world, channel, "v1", first.id());
        assertEquals(1, numberOf(world, first.id(), "v1"), "the first post of the series is episode 1");
        world.service.assign(null, post.id(), new AssignmentCommand(second.id(), false, true), EDITOR);

        world.service.assign(null, post.id(), new AssignmentCommand(first.id(), false, true), EDITOR);

        assertEquals(1, numberOf(world, first.id(), "v1"), "the post keeps episode number 1 after the round trip");
        List<Episode> episodes = world.service.episodes(null, first.id());
        assertEquals(1, episodes.size(), "no other post received that number");
        assertEquals(post.id(), episodes.get(0).post().id(), "the number still belongs to the same post");
    }

    @Test
    void aPostThatLeavesASeriesDoesNotFreeItsNumber() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, SeriesFixture.draft("Serie", "#serie"));
        UUID channel = world.channel("UC1");
        for (int i = 1; i <= 5; i++) {
            assignNew(world, channel, "v" + i, series.id());
        }
        ExternalPost sixth = assignNew(world, channel, "v6", series.id());
        assertEquals(6, numberOf(world, series.id(), "v6"), "the sixth post is episode 6");
        world.service.assign(null, sixth.id(), new AssignmentCommand(null, true, true), EDITOR);

        assignNew(world, channel, "v7", series.id());

        assertEquals(7, numberOf(world, series.id(), "v7"), "the next post receives 7, not the freed 6");
    }

    @Test
    void aPostMarkedNoSeriesHasNoEpisodeNumberInTheSeriesList() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, SeriesFixture.draft("Serie", "#serie"));
        UUID channel = world.channel("UC1");
        ExternalPost post = SeriesFixture.post(channel, "v1", ExternalPostFormat.SHORT, SeriesFixture.NOW);
        world.posts.insert(null, post);
        world.service.assign(null, post.id(), new AssignmentCommand(null, true, false), EDITOR);

        List<Episode> episodes = world.service.episodes(null, series.id());

        assertFalse(episodes.stream().anyMatch(e -> e.post().id().equals(post.id())),
                "a post marked no series has no episode number");
        assertTrue(episodes.isEmpty(), "the series has no episodes yet");
    }

    private static ExternalPost assignNew(SeriesFixture world, UUID channel, String itemId, UUID series) throws SQLException {
        ExternalPost post = SeriesFixture.post(channel, itemId, ExternalPostFormat.SHORT, SeriesFixture.NOW.minus(Duration.ofDays(1)));
        world.posts.insert(null, post);
        world.service.assign(null, post.id(), new AssignmentCommand(series, false, false), EDITOR);
        return post;
    }

    private static int numberOf(SeriesFixture world, UUID series, String itemId) throws SQLException {
        return world.service.episodes(null, series).stream()
                .filter(e -> e.post().platformItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no episode for " + itemId))
                .number();
    }
}
