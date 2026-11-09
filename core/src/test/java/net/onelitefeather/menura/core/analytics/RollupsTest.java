package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.Rollups.Grain;
import net.onelitefeather.menura.core.analytics.Rollups.Input;
import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.Rollups.SnapshotInput;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rollup rules: daily sums equal the raw values, weeks add up their days, unavailable is not zero. */
class RollupsTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private static final UUID CHANNEL = UUID.nameUUIDFromBytes("channel-rollups".getBytes());
    private static final UUID SERIES = UUID.nameUUIDFromBytes("series-rollups".getBytes());

    private static Grain shorts() {
        return Grain.platform(CHANNEL, "short", 18, SERIES, "tpl-a", 3);
    }

    private static Input value(UUID post, Grain grain, String metric, Long value) {
        return new Input(post, grain, metric, value);
    }

    private static Rollup row(List<Rollup> rows, Grain grain, String metric) {
        return rows.stream().filter(r -> r.grain().equals(grain) && r.metric().equals(metric)).findFirst().orElseThrow();
    }

    @Test
    void aDailyRowIsTheSumOfTheLatestValuesOfItsPosts() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<Input> inputs = List.of(value(first, shorts(), Metrics.VIEWS, 100L),
                value(second, shorts(), Metrics.VIEWS, 50L));

        Rollup views = row(Rollups.daily(MONDAY, inputs), shorts(), Metrics.VIEWS);

        assertEquals(150L, views.total(), "the daily views are the sum of the latest values of the posts");
        assertEquals(2, views.valuesCount(), "two posts contributed a views value");
        assertEquals(2, views.posts(), "two distinct posts are in the grain");
    }

    @Test
    void aPlatformGrainHasARowForEveryMetricEvenWhenNoPostProvidesIt() {
        List<Rollup> rows = Rollups.daily(MONDAY, List.of(value(UUID.randomUUID(), shorts(), Metrics.VIEWS, 10L)));

        Rollup watch = row(rows, shorts(), Metrics.WATCH_TIME);

        assertEquals(0, watch.valuesCount(), "watch time is unavailable: no value was summed");
        assertEquals(0L, watch.total(), "the row carries no value and is never read as a measured zero");
        assertEquals(1, watch.posts(), "the post is counted in the grain on every row");
    }

    @Test
    void theDimensionsOfAGrainStayApartInTheDailyRows() {
        Grain otherFormat = Grain.platform(CHANNEL, "long", 18, SERIES, "tpl-a", 3);
        List<Input> inputs = List.of(value(UUID.randomUUID(), shorts(), Metrics.VIEWS, 10L),
                value(UUID.randomUUID(), otherFormat, Metrics.VIEWS, 20L));

        List<Rollup> rows = Rollups.daily(MONDAY, inputs);

        assertEquals(10L, row(rows, shorts(), Metrics.VIEWS).total(), "the short grain sums only its own post");
        assertEquals(20L, row(rows, otherFormat, Metrics.VIEWS).total(), "the long grain sums only its own post");
    }

    @Test
    void aWeeklyRowIsTheSumOfTheDailyRowsOfTheWeek() {
        Grain grain = shorts();
        List<Rollup> monday = Rollups.daily(MONDAY, List.of(value(UUID.randomUUID(), grain, Metrics.VIEWS, 100L)));
        List<Rollup> tuesday = Rollups.daily(MONDAY.plusDays(1), List.of(value(UUID.randomUUID(), grain, Metrics.VIEWS, 30L),
                value(UUID.randomUUID(), grain, Metrics.VIEWS, 20L)));
        List<Rollup> daily = new ArrayList<>(monday);
        daily.addAll(tuesday);

        Rollup week = row(Rollups.weekly(MONDAY, daily), grain, Metrics.VIEWS);

        assertEquals(150L, week.total(), "the week adds up its days");
        assertEquals(3, week.valuesCount(), "the week counts the values of its days");
        assertEquals(3, week.posts(), "the days hold three different posts");
        assertEquals(MONDAY, week.period(), "the weekly row is dated by its Monday");
    }

    @Test
    void anEstimateStaysApartFromTheMeasuredValueOfTheSameMetric() {
        Grain measured = new Grain(CHANNEL, null, null, SERIES, null, null, "joins", false);
        Grain estimated = new Grain(CHANNEL, null, null, SERIES, null, null, "joins", true);
        List<Input> inputs = List.of(value(null, measured, "first_joins", 5L), value(null, estimated, "first_joins", 3L));

        List<Rollup> rows = Rollups.daily(MONDAY, inputs);

        assertEquals(5L, row(rows, measured, "first_joins").total(), "the measured value is kept as it was stored");
        assertEquals(3L, row(rows, estimated, "first_joins").total(), "the estimate is a row of its own, flagged");
        assertTrue(row(rows, estimated, "first_joins").grain().estimate(), "the estimate flag is kept on the row");
        assertFalse(row(rows, measured, "first_joins").grain().estimate(), "the measured row is not flagged");
    }

    @Test
    void theOffsetGroupsKeepTheValuesOfThePostsThatHaveOne() {
        List<SnapshotInput> snapshots = List.of(
                new SnapshotInput(UUID.randomUUID(), CHANNEL, 18, 1, 1440, Metrics.VIEWS, 100L),
                new SnapshotInput(UUID.randomUUID(), CHANNEL, 18, 1, 1440, Metrics.VIEWS, 200L),
                new SnapshotInput(UUID.randomUUID(), CHANNEL, 18, 1, 1440, Metrics.VIEWS, null),
                new SnapshotInput(UUID.randomUUID(), CHANNEL, 9, 1, 1440, Metrics.VIEWS, null));

        List<OffsetGroup> groups = Rollups.offsets(MONDAY, snapshots);

        assertEquals(1, groups.size(), "a group without any value is not stored");
        assertEquals(List.of(100L, 200L), groups.get(0).values(), "the absent snapshot is not a value");
        assertEquals(18, groups.get(0).publishHour(), "the group keeps the publish hour");
    }
}
