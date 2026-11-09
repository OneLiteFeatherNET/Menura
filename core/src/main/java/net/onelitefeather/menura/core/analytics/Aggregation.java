package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The aggregation of rollup rows (change add-content-analytics): sums per group, averages for the percentage, and the
 * median per slot at equal age. No I/O, so the rules are tested without a database.
 *
 * <p>An unavailable metric is never summed as zero: a group with no value for a metric reports it as null with a count of
 * zero, and the count of values used is always reported next to the value.
 */
public final class Aggregation {

    private Aggregation() {
    }

    /** One metric of a group: its value (null when unavailable), the number of values used, and its kind. */
    public record Total(String name, Double value, int posts, boolean average) {
    }

    /** One group: its key, the number of distinct posts in it, and every metric in the fixed order. */
    public record Group(String key, int posts, List<Total> metrics) {
    }

    /** One slot of an equal-age comparison: the number of posts with a value and the median of their values. */
    public record Slot(int slot, int posts, Double median) {
    }

    /**
     * Groups the rollup rows by key and aggregates every metric of each group. Groups come back sorted by key.
     */
    public static List<Group> groups(Collection<Rollup> rows, Function<Rollup, String> keyOf) {
        Map<String, List<Rollup>> byKey = new TreeMap<>();
        for (Rollup row : rows) {
            byKey.computeIfAbsent(keyOf.apply(row), key -> new ArrayList<>()).add(row);
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<Rollup>> entry : byKey.entrySet()) {
            groups.add(total(entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    /**
     * The aggregate of rollup rows as one group. The post count is the number of distinct posts of the platform views
     * rows, which carry every post of their grain once.
     */
    public static Group total(String key, Collection<Rollup> rows) {
        int posts = 0;
        List<Total> totals = new ArrayList<>();
        for (Rollup row : rows) {
            if (Metrics.VIEWS.equals(row.metric()) && row.grain().platformValue()) {
                posts += row.posts();
            }
        }
        for (String metric : Metrics.ALL) {
            long values = 0;
            long sum = 0;
            for (Rollup row : rows) {
                if (metric.equals(row.metric())) {
                    values += row.valuesCount();
                    sum += row.total();
                }
            }
            boolean average = Metrics.isAverage(metric);
            Double value = null;
            if (values > 0) {
                value = average ? (double) sum / values : (double) sum;
            }
            totals.add(new Total(metric, value, (int) values, average));
        }
        return new Group(key, posts, totals);
    }

    /**
     * The median of one metric per slot at equal age. Every slot from {@code first} to {@code last} is reported, with a
     * count of zero and a null median when no post of the slot has a value.
     */
    public static List<Slot> medianBySlot(Collection<OffsetGroup> groups, String metric, int first, int last,
                                          ToIntFunction<OffsetGroup> slotOf) {
        Map<Integer, List<Long>> bySlot = new TreeMap<>();
        for (OffsetGroup group : groups) {
            if (metric.equals(group.metric())) {
                bySlot.computeIfAbsent(slotOf.applyAsInt(group), slot -> new ArrayList<>()).addAll(group.values());
            }
        }
        List<Slot> slots = new ArrayList<>();
        for (int slot = first; slot <= last; slot++) {
            List<Long> values = bySlot.getOrDefault(slot, List.of());
            slots.add(new Slot(slot, values.size(), median(values)));
        }
        return slots;
    }

    /** The median of the values, the mean of the two middle values for an even count; null for no values. */
    public static Double median(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return (double) sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }
}
