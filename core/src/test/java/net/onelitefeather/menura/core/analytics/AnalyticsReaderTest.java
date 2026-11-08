package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.AnalyticsReader.By;
import net.onelitefeather.menura.core.analytics.support.AnalyticsFixture;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The read models over the stored values, with an injected clock and in-memory stores. */
class AnalyticsReaderTest {

    /** 2026-11-09 18:00 in Europe/Berlin, which is a Monday at the 18 o'clock slot. */
    private static final Instant MONDAY_EVENING = Instant.parse("2026-11-09T17:00:00Z");

    @Test
    void theMedianAtTwentyFourHoursIsTakenPerPublishHourOverThePostsOfThatHour() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC1");
        Instant now = MONDAY_EVENING.plus(Duration.ofDays(3));
        world.channels.clock.advance(Duration.between(world.now(), now));
        publishAndReport(world, channel, "a", MONDAY_EVENING, 100L);
        publishAndReport(world, channel, "b", MONDAY_EVENING, 200L);
        publishAndReport(world, channel, "c", MONDAY_EVENING, 900L);
        world.collector.collect();

        AnalyticsReader.EqualAge comparison = world.reader.equalAge(Metrics.VIEWS, Duration.ofHours(24), By.HOUR, null);

        Aggregation.Slot eighteen = comparison.slots().get(18);
        assertEquals(3, eighteen.posts(), "the three posts of the 18 o'clock slot are used");
        assertEquals(200.0, eighteen.median(), "the median of 100, 200 and 900 is 200");
    }

    @Test
    void aPostYoungerThanTheOffsetIsNotCounted() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC2");
        world.channels.clock.advance(Duration.ofHours(10));
        publishAndReport(world, channel, "young", world.channels.clock.instant().minus(Duration.ofHours(10)), 500L);
        world.collector.collect();

        AnalyticsReader.EqualAge comparison = world.reader.equalAge(Metrics.VIEWS, Duration.ofHours(24), By.HOUR, null);

        assertEquals(0, comparison.slots().stream().mapToInt(Aggregation.Slot::posts).sum(),
                "a post of ten hours has no +24 hour snapshot and is not counted");
    }

    @Test
    void aMetricNoPostProvidesIsUnavailableInItsSlot() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC3");
        world.channels.clock.advance(Duration.between(world.now(), MONDAY_EVENING.plus(Duration.ofDays(2))));
        publishAndReport(world, channel, "a", MONDAY_EVENING, 100L);
        world.collector.collect();

        AnalyticsReader.EqualAge watch = world.reader.equalAge(Metrics.WATCH_TIME, Duration.ofHours(24), By.HOUR, null);

        assertNull(watch.slots().get(18).median(), "watch time is unavailable in the slot, not zero");
        assertEquals(0, watch.slots().get(18).posts(), "no post of the slot has a watch time value");
    }

    @Test
    void anOffsetThatIsNotOnTheScheduleIsRefusedWithProblemDetails() {
        AnalyticsFixture world = new AnalyticsFixture(true);

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.reader.equalAge(Metrics.VIEWS, Duration.ofHours(5), By.HOUR, null));

        assertEquals(400, refused.status(), "an offset that is not on the schedule is a bad request");
    }

    @Test
    void aLaterReadingReplacesTheLatestValueOfTheChannelTotal() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC4");
        publishAndReport(world, channel, "a", world.now().minus(Duration.ofHours(5)), 100L);
        world.collector.collect();
        world.advance(Duration.ofHours(1));
        world.platform.report("a", Map.of(Metrics.VIEWS, 130L));
        world.collector.collect();

        AnalyticsReader.Overview overview = world.reader.overview(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
                Set.of(Role.VIEWER));

        assertEquals(130.0, overview.totals().metrics().stream().filter(t -> t.name().equals(Metrics.VIEWS))
                .findFirst().orElseThrow().value(), "the total is the newest cumulative value, not the sum of readings");
    }

    @Test
    void theSeriesComparisonRanksByEngagedViewsNotByViews() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC5");
        UUID seriesA = UUID.nameUUIDFromBytes("series-a".getBytes());
        UUID seriesB = UUID.nameUUIDFromBytes("series-b".getBytes());
        ExternalPost a = publishAndReport(world, channel, "a", world.now().minus(Duration.ofHours(5)), 1000L);
        ExternalPost b = publishAndReport(world, channel, "b", world.now().minus(Duration.ofHours(5)), 100L);
        world.metrics.assign(a.id(), seriesA);
        world.metrics.assign(b.id(), seriesB);
        world.platform.report("a", Map.of(Metrics.VIEWS, 1000L, Metrics.ENGAGED_VIEWS, 10L));
        world.platform.report("b", Map.of(Metrics.VIEWS, 100L, Metrics.ENGAGED_VIEWS, 90L));
        world.collector.collect();

        AnalyticsReader.SeriesComparison comparison = world.reader.series(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31), Set.of(Role.VIEWER));

        assertEquals(seriesB, comparison.series().get(0).seriesId(), "the series with more engaged views ranks first");
    }

    @Test
    void theAnalyticsOfAMissingPostAnswerNotFound() {
        AnalyticsFixture world = new AnalyticsFixture(true);

        ProblemException missing = assertThrows(ProblemException.class,
                () -> world.reader.post(UUID.nameUUIDFromBytes("missing".getBytes())));

        assertEquals(404, missing.status(), "a post that does not exist answers 404");
    }

    private static ExternalPost publishAndReport(AnalyticsFixture world, UUID channel, String item, Instant publishedAt,
                                                 long views) throws SQLException {
        ExternalPost post = world.publishedPost(channel, item, publishedAt, "short");
        world.platform.report(item, Map.of(Metrics.VIEWS, views));
        return post;
    }
}
