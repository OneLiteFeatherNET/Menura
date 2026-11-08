package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.analytics.support.AnalyticsFixture;
import net.onelitefeather.menura.core.analytics.support.InMemoryMetricStore;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.post.ExternalPost;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scheduled collection against a scripted platform, with an injected clock and in-memory stores. */
class MetricCollectorTest {

    @Test
    void aScheduledRunStoresOnePointPerMetricOfEachPost() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC1");
        ExternalPost post = world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 120L, Metrics.LIKES, 7L));

        world.collector.collect();

        List<InMemoryMetricStore.Point> points = world.metrics.points();
        assertEquals(2, points.size(), "one point for each metric the platform reported");
        assertTrue(points.stream().allMatch(p -> p.postId().equals(post.id())), "every point names its post");
    }

    @Test
    void aMetricTheChannelDoesNotProvideIsStoredAsAbsentNotAsZero() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC2");
        world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 120L));

        world.collector.collect();

        assertFalse(world.metrics.points().stream().anyMatch(p -> p.metric().equals(Metrics.WATCH_TIME)),
                "no watch time point is stored when the platform does not provide watch time");
    }

    @Test
    void aFailingPostKeepsThePointsOfThePostsCollectedBeforeIt() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        // One item per call: a failing call loses its own batch, so the posts before it must be in earlier calls.
        world.platform.batchSize(1);
        UUID channel = world.connectedChannel("UC3");
        ExternalPost first = world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.publishedPost(channel, "v2", world.now().minus(Duration.ofHours(4)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 10L));
        world.platform.failMetricsOf("v2", new PlatformException(PlatformException.Kind.UNAVAILABLE,
                "The platform could not be reached.", null));

        world.collector.collect();

        assertEquals(1, world.metrics.points().size(), "the first post keeps its point");
        assertEquals(first.id(), world.metrics.points().get(0).postId(), "the stored point belongs to the first post");
        CollectionStatus status = world.metrics.status(channel).orElseThrow();
        assertEquals("failed", status.outcome(), "the channel is recorded as failed");
        assertEquals(MetricCollector.UNREACHABLE, status.failureReason(), "the failure reason is the fixed text");
    }

    @Test
    void aPlatformWithoutMetricsIsRecordedAsNotSupportedAndStoresNoPoints() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(false);
        UUID channel = world.connectedChannel("UC4");
        world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 10L));

        world.collector.collect();

        assertTrue(world.metrics.points().isEmpty(), "nothing is stored for a platform without metrics");
        assertEquals("not_supported", world.metrics.status(channel).orElseThrow().outcome(),
                "the channel says the platform does not report metrics");
    }

    @Test
    void theSnapshotOfAnOffsetIsTakenInTheFirstRunAtOrAfterIt() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC5");
        ExternalPost post = world.publishedPost(channel, "v1", world.now(), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 30L));
        world.collector.collect();
        assertTrue(world.metrics.snapshotsOf(null, post.id()).isEmpty(), "no offset is reached at the moment of publication");

        world.advance(Duration.ofMinutes(65));
        world.collector.collect();

        SnapshotValue oneHour = snapshot(world.metrics.snapshotsOf(null, post.id()), 60, Metrics.VIEWS);
        assertEquals(30L, oneHour.value(), "the +1 hour snapshot holds the cumulative value of the run");
        assertEquals(world.now(), oneHour.asOf(), "the snapshot carries the as-of time of the run that took it");
        assertEquals(65, oneHour.ageMinutes(), "the snapshot carries its age at that time");
    }

    @Test
    void aMetricTheChannelDoesNotProvideIsAbsentInTheSnapshot() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC6");
        ExternalPost post = world.publishedPost(channel, "v1", world.now(), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 30L));
        world.advance(Duration.ofHours(2));

        world.collector.collect();

        SnapshotValue watch = snapshot(world.metrics.snapshotsOf(null, post.id()), 60, Metrics.WATCH_TIME);
        assertNull(watch.value(), "the watch time of the snapshot is absent, not zero");
    }

    @Test
    void dailySnapshotsAreTakenForTheEighthAndNinthDay() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC7");
        ExternalPost post = world.publishedPost(channel, "v1", world.now().minus(Duration.ofDays(9)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 500L));

        world.collector.collect();

        assertTrue(hasOffset(world.metrics.snapshotsOf(null, post.id()), Duration.ofDays(8)), "day eight has a snapshot");
        assertTrue(hasOffset(world.metrics.snapshotsOf(null, post.id()), Duration.ofDays(9)), "day nine has a snapshot");
    }

    @Test
    void aRemovedPostIsNotCollected() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC8");
        ExternalPost post = world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.posts.update(null, post.markedRemoved(world.now()));

        world.collector.collect();

        assertTrue(world.platform.reads().isEmpty(), "the platform is not asked about a removed post");
    }

    @Test
    void thePartitionOfTheNextMonthExistsBeforeTheMonthChanges() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC9");
        world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 1L));
        world.advance(Duration.ofDays(28));

        world.collector.collect();

        YearMonth november = YearMonth.of(2026, 11);
        assertTrue(world.metrics.hasPartition(november), "the partition of the current month exists");
        assertTrue(world.metrics.hasPartition(november.plusMonths(1)), "the partition of the next month exists ahead");
    }

    @Test
    void aLatestValueIsReplacedByTheNewerReadingNotAddedToIt() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC10");
        ExternalPost post = world.publishedPost(channel, "v1", world.now().minus(Duration.ofHours(5)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 100L));
        world.collector.collect();
        world.advance(Duration.ofHours(4));
        world.platform.report("v1", Map.of(Metrics.VIEWS, 150L));

        world.collector.collect();

        Instant latest = world.now();
        assertEquals(150L, world.metrics.latestOf(null, post.id()).stream()
                .filter(v -> v.metric().equals(Metrics.VIEWS)).findFirst().orElseThrow().value(),
                "the latest views are the cumulative value of the newest run");
        assertEquals(latest, world.metrics.latestOf(null, post.id()).get(0).collectedAt(),
                "the latest value carries the time of the newest run");
    }

    @Test
    void aPostWithNoDueOffsetAndAFreshValueMakesNoPlatformCall() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC11");
        world.publishedPost(channel, "v1", world.now(), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 5L));
        world.collector.collect();
        int callsAfterFirstRun = world.platform.batches().size();
        world.advance(Duration.ofMinutes(10));

        world.collector.collect();

        assertEquals(callsAfterFirstRun, world.platform.batches().size(),
                "a post with no due offset and a value of the last day makes no platform call");
    }

    @Test
    void severalDuePostsOfOneChannelMakeExactlyOneBatchCall() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC12");
        for (String item : List.of("v1", "v2", "v3")) {
            world.publishedPost(channel, item, world.now().minus(Duration.ofHours(5)), "short");
            world.platform.report(item, Map.of(Metrics.VIEWS, 1L));
        }

        world.collector.collect();

        assertEquals(1, world.platform.batches().size(), "three due posts of one channel are read in one call");
        assertEquals(3, world.platform.batches().get(0).size(), "the one call names all three posts");
    }

    @Test
    void thePlatformsBatchSizeSplitsTheCallsOfAChannel() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC13");
        world.platform.batchSize(2);
        for (String item : List.of("v1", "v2", "v3")) {
            world.publishedPost(channel, item, world.now().minus(Duration.ofHours(5)), "short");
            world.platform.report(item, Map.of(Metrics.VIEWS, 1L));
        }

        world.collector.collect();

        assertEquals(List.of(2, 1), world.platform.batches().stream().map(List::size).toList(),
                "three posts with a batch size of two make a call for two and a call for one");
    }

    @Test
    void aPostPastTheDailySnapshotsIsCollectedAgainOnceItsValueIsADayOld() throws SQLException {
        AnalyticsFixture world = new AnalyticsFixture(true);
        UUID channel = world.connectedChannel("UC14");
        world.publishedPost(channel, "v1", world.now().minus(Duration.ofDays(400)), "short");
        world.platform.report("v1", Map.of(Metrics.VIEWS, 9L));
        world.collector.collect();
        world.advance(Duration.ofHours(25));

        world.collector.collect();

        assertEquals(2, world.platform.batches().size(),
                "no offset is left, but the value is a day old, so the post is read again");
    }

    private static SnapshotValue snapshot(List<SnapshotValue> snapshots, int minutes, String metric) {
        return snapshots.stream().filter(s -> s.offsetMinutes() == minutes && s.metric().equals(metric))
                .findFirst().orElseThrow();
    }

    private static boolean hasOffset(List<SnapshotValue> snapshots, Duration offset) {
        return snapshots.stream().anyMatch(s -> s.offsetMinutes() == offset.toMinutes());
    }
}
