package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.Rollups.Grain;
import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The aggregation of rollup rows and the median of equal-age groups, without a database. */
class AggregationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final UUID CHANNEL = UUID.nameUUIDFromBytes("channel".getBytes());

    private static Rollup row(String format, String metric, int posts, int values, long total) {
        return new Rollup(DAY, Grain.platform(CHANNEL, format, 18, null, null, null), metric, posts, values, total);
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
        Aggregation.Group group = Aggregation.total("all", List.of(row("short", Metrics.VIEWS, 1, 1, 40L),
                row("short", Metrics.WATCH_TIME, 1, 0, 0L)));

        Aggregation.Total watch = total(group, Metrics.WATCH_TIME);

        assertNull(watch.value(), "watch time is unavailable, not zero, when no post provides it");
        assertEquals(0, watch.posts(), "no post contributed a watch time value");
    }

    @Test
    void countsAreSummedOverTheRows() {
        Aggregation.Group group = Aggregation.total("all", List.of(row("short", Metrics.VIEWS, 1, 1, 100L),
                row("long", Metrics.VIEWS, 1, 1, 50L)));

        Aggregation.Total views = total(group, Metrics.VIEWS);

        assertEquals(150.0, views.value(), "the views of two grains add up");
        assertEquals(2, views.posts(), "two values contributed to the views");
    }

    @Test
    void anAveragePercentageIsTheSumOverTheValuesNotTheSum() {
        Aggregation.Group group = Aggregation.total("all", List.of(
                row("short", Metrics.AVERAGE_VIEW_PERCENTAGE, 1, 1, 40L),
                row("short", Metrics.AVERAGE_VIEW_PERCENTAGE, 1, 1, 60L)));

        Aggregation.Total average = total(group, Metrics.AVERAGE_VIEW_PERCENTAGE);

        assertEquals(50.0, average.value(), "the average view percentage is the mean of 40 and 60");
        assertTrue(average.average(), "the metric is flagged as an average");
    }

    @Test
    void groupsAreSortedByTheirKeyAndCountTheDistinctPostsOfTheViewsRows() {
        List<Rollup> rows = List.of(
                new Rollup(DAY, Grain.platform(CHANNEL, "b", 18, null, null, null), Metrics.VIEWS, 2, 2, 3L),
                new Rollup(DAY, Grain.platform(CHANNEL, "b", 18, null, null, null), Metrics.LIKES, 2, 1, 1L),
                new Rollup(DAY, Grain.platform(CHANNEL, "a", 18, null, null, null), Metrics.VIEWS, 1, 1, 1L));

        List<Aggregation.Group> groups = Aggregation.groups(rows, Rollup -> Rollup.grain().format());

        assertEquals("a", groups.get(0).key(), "the groups come back sorted by key");
        assertEquals(1, groups.get(0).posts(), "one post in group a");
        assertEquals(2, groups.get(1).posts(), "two posts in group b, counted once each");
    }

    @Test
    void theMedianPerSlotCoversEverySlotAndCountsOnlyTheValuesThatExist() {
        List<OffsetGroup> groups = List.of(
                new OffsetGroup(DAY, CHANNEL, 18, 1, 1440, Metrics.VIEWS, List.of(100L, 200L)),
                new OffsetGroup(DAY, CHANNEL, 18, 1, 1440, Metrics.VIEWS, List.of(900L)));

        List<Aggregation.Slot> slots = Aggregation.medianBySlot(groups, Metrics.VIEWS, 0, 23, OffsetGroup::publishHour);

        assertEquals(24, slots.size(), "every hour of the day is reported");
        assertEquals(3, slots.get(18).posts(), "the three values of the hour are used");
        assertEquals(200.0, slots.get(18).median(), "the median of 100, 200 and 900 is 200");
        assertEquals(0, slots.get(3).posts(), "an hour without posts has no posts");
        assertNull(slots.get(3).median(), "an hour without posts has no median");
    }

    private static Aggregation.Total total(Aggregation.Group group, String metric) {
        return group.metrics().stream().filter(t -> t.name().equals(metric)).findFirst().orElseThrow();
    }
}
