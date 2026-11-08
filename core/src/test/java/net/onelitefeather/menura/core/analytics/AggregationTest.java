package net.onelitefeather.menura.core.analytics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregationTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-09T17:00:00Z");

    private static Observation view(UUID post, String metric, Long value) {
        return new Observation(post, UUID.nameUUIDFromBytes("channel".getBytes()), "metrics", "short", null, PUBLISHED,
                metric, value);
    }

    @Test
    void theMedianOfAnOddCountIsTheMiddleValue() {
        assertEquals(200.0, Aggregation.median(List.of(900L, 100L, 200L)), "the middle of 100, 200 and 900 is 200");
    }

    @Test
    void theMedianOfAnEvenCountIsTheMeanOfTheTwoMiddleValues() {
        assertEquals(150.0, Aggregation.median(List.of(100L, 200L)), "the mean of 100 and 200 is 150");
    }

    @Test
    void theMedianOfNoValueIsNullNotZero() {
        assertNull(Aggregation.median(List.of()), "no value gives no median, never zero");
    }

    @Test
    void aMetricNoPostProvidesIsUnavailableInTheGroupNotZero() {
        UUID post = UUID.randomUUID();
        Aggregation.Group group = Aggregation.total("all", List.of(view(post, Metrics.VIEWS, 40L)));

        Aggregation.Total watch = total(group, Metrics.WATCH_TIME);

        assertNull(watch.value(), "watch time is unavailable, not zero, when no post provides it");
        assertEquals(0, watch.posts(), "no post contributed a watch time value");
    }

    @Test
    void countsAreSummedOverPosts() {
        Aggregation.Group group = Aggregation.total("all", List.of(view(UUID.randomUUID(), Metrics.VIEWS, 100L),
                view(UUID.randomUUID(), Metrics.VIEWS, 50L)));

        Aggregation.Total views = total(group, Metrics.VIEWS);

        assertEquals(150.0, views.value(), "the views of two posts add up");
        assertEquals(2, views.posts(), "two posts contributed a views value");
    }

    @Test
    void anAveragePercentageIsAveragedNotSummed() {
        Aggregation.Group group = Aggregation.total("all", List.of(
                view(UUID.randomUUID(), Metrics.AVERAGE_VIEW_PERCENTAGE, 40L),
                view(UUID.randomUUID(), Metrics.AVERAGE_VIEW_PERCENTAGE, 60L)));

        Aggregation.Total average = total(group, Metrics.AVERAGE_VIEW_PERCENTAGE);

        assertEquals(50.0, average.value(), "the average view percentage is the mean of 40 and 60");
        assertTrue(average.average(), "the metric is flagged as an average");
    }

    @Test
    void groupsAreSortedByTheirKeyAndCountDistinctPosts() {
        UUID first = UUID.randomUUID();
        List<Observation> observations = List.of(view(first, Metrics.VIEWS, 1L), view(first, Metrics.LIKES, 2L),
                view(UUID.randomUUID(), Metrics.VIEWS, 3L));

        List<Aggregation.Group> groups = Aggregation.groups(observations, o -> o.metric().equals(Metrics.VIEWS) ? "b" : "a");

        assertEquals("a", groups.get(0).key(), "the groups come back sorted by key");
        assertEquals(1, groups.get(0).posts(), "the like of the first post is one post, not two");
        assertEquals(2, groups.get(1).posts(), "two distinct posts have views in group b");
    }

    @Test
    void theMedianPerSlotCoversEverySlotAndSkipsAbsentSnapshots() {
        List<Observation> snapshots = List.of(
                new Observation(UUID.randomUUID(), null, "metrics", "short", null, PUBLISHED, Metrics.VIEWS, 100L),
                new Observation(UUID.randomUUID(), null, "metrics", "short", null, PUBLISHED, Metrics.VIEWS, 200L),
                new Observation(UUID.randomUUID(), null, "metrics", "short", null, PUBLISHED, Metrics.VIEWS, 900L),
                new Observation(UUID.randomUUID(), null, "metrics", "short", null, PUBLISHED, Metrics.VIEWS, null));

        List<Aggregation.Slot> slots = Aggregation.medianBySlot(snapshots, Metrics.VIEWS, 0, 23,
                o -> new PublishSlot(java.time.ZoneId.of("Europe/Berlin")).hour(o.publishedAt()));

        assertEquals(24, slots.size(), "every hour of the day is reported");
        Aggregation.Slot eighteen = slots.get(18);
        assertEquals(3, eighteen.posts(), "the absent snapshot is not counted");
        assertEquals(200.0, eighteen.median(), "the median of 100, 200 and 900 is 200");
        assertEquals(0, slots.get(3).posts(), "an hour without posts has no posts");
        assertNull(slots.get(3).median(), "an hour without posts has no median");
    }

    private static Aggregation.Total total(Aggregation.Group group, String metric) {
        return group.metrics().stream().filter(t -> t.name().equals(metric)).findFirst().orElseThrow();
    }
}
