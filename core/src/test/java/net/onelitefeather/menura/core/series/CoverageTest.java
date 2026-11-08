package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.series.SeriesReports.Overview;
import net.onelitefeather.menura.core.series.SeriesReports.WeekCoverage;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weekly coverage of published shorts and the audience warning. The clock is fixed on a Monday, so all posts of these tests
 * fall into the current calendar week.
 */
class CoverageTest {

    private static final UUID EDITOR = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");

    @Test
    void aWeekAtNinetyPercentIsNotFlagged() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        publishShorts(world, channel, 10, 9);

        WeekCoverage week = currentWeek(world, channel);

        assertEquals(10, week.shorts(), "the ten published shorts are counted");
        assertEquals(90, week.percent(), "nine of ten covered is 90 percent");
        assertFalse(week.flagged(), "a week at the threshold is not flagged");
    }

    @Test
    void aWeekBelowNinetyPercentIsFlagged() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        publishShorts(world, channel, 10, 8);

        WeekCoverage week = currentWeek(world, channel);

        assertEquals(80, week.percent(), "eight of ten covered is 80 percent");
        assertTrue(week.flagged(), "a week below the threshold is flagged");
    }

    @Test
    void unpublishedDraftsAreNotCounted() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        publishShorts(world, channel, 10, 10);
        for (int i = 0; i < 2; i++) {
            ExternalPost draft = SeriesFixture.post(channel, "draft" + i, "short",
                    SeriesFixture.NOW.plus(Duration.ofDays(1)));
            world.posts.insert(null, draft);
            world.service.assign(null, draft.id(), new AssignmentCommand(null, true, false), EDITOR);
        }

        WeekCoverage week = currentWeek(world, channel);

        assertEquals(10, week.shorts(), "posts that are not published yet are not counted");
        assertEquals(100, week.percent(), "the covered share is measured over the published shorts only");
    }

    @Test
    void aWeekWithoutPublishedShortsIsReportedAsEmptyAndNotFlagged() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");

        WeekCoverage week = currentWeek(world, channel);

        assertEquals(0, week.shorts(), "no shorts in the week");
        assertNull(week.percent(), "a week without shorts has no percentage");
        assertFalse(week.flagged(), "a week without shorts is not flagged");
    }

    @Test
    void theNoSeriesShareIsReportedSeparately() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        publishShorts(world, channel, 10, 9);

        WeekCoverage week = currentWeek(world, channel);

        assertEquals(9, week.covered(), "covered includes the no-series marks");
        assertEquals(9, week.noSeries(), "the no-series share is shown separately");
    }

    @Test
    void anOverviewWithoutAnActivePlayersSeriesWarnsThatNoSeriesTargetsPlayers() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("Devs", "#devs"));

        Overview overview = world.service.overview(null, 1);

        assertEquals(java.util.List.of(SeriesService.NO_PLAYERS_SERIES), overview.warnings(),
                "the warning names the missing players series");
    }

    @Test
    void anActivePlayersSeriesRemovesTheWarning() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, withAudience(SeriesFixture.draft("Spieler", "#spieler"), "players"));

        Overview overview = world.service.overview(null, 1);

        assertTrue(overview.warnings().isEmpty(), "an active players series means no warning");
    }

    @Test
    void anInactivePlayersSeriesDoesNotRemoveTheWarning() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, withActive(withAudience(SeriesFixture.draft("Spieler", "#spieler"), "players"), false));

        Overview overview = world.service.overview(null, 1);

        assertEquals(java.util.List.of(SeriesService.NO_PLAYERS_SERIES), overview.warnings(),
                "an inactive players series does not count");
    }

    private static void publishShorts(SeriesFixture world, UUID channel, int total, int covered) throws SQLException {
        for (int i = 0; i < total; i++) {
            ExternalPost post = SeriesFixture.post(channel, "short" + i, "short", SeriesFixture.NOW);
            world.posts.insert(null, post);
            if (i < covered) {
                world.service.assign(null, post.id(), new AssignmentCommand(null, true, false), EDITOR);
            }
        }
    }

    private static WeekCoverage currentWeek(SeriesFixture world, UUID channel) throws SQLException {
        Overview overview = world.service.overview(null, 1);
        return overview.coverage().stream().filter(c -> c.channelId().equals(channel)).findFirst()
                .orElseThrow().weeks().get(0);
    }

    private static SeriesDraft withAudience(SeriesDraft d, String audience) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), audience, d.weekday(), d.postingSlot(), d.active(), d.playlists());
    }

    private static SeriesDraft withActive(SeriesDraft d, boolean active) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), active, d.playlists());
    }
}
