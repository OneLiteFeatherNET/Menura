package net.onelitefeather.menura.core.analytics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The pure aggregation of observations (change add-content-analytics): sums and averages per group, and the median per
 * publish slot at equal age. No I/O, so the rules are tested without a database.
 *
 * <p>An unavailable metric is never summed as zero: a group with no value for a metric reports it as null with a count of
 * zero, and the count of values used is always reported next to the value.
 */
public final class Aggregation {

    private Aggregation() {
    }

    /** One metric of a group: its value (null when unavailable), the number of posts that contributed, and its kind. */
    public record Total(String name, Double value, int posts, boolean average) {
    }

    /** One group: its key, the number of distinct posts in it, and every metric in the fixed order. */
    public record Group(String key, int posts, List<Total> metrics) {
    }

    /** One slot of an equal-age comparison: the number of posts with a value and the median of their values. */
    public record Slot(int slot, int posts, Double median) {
    }

    /**
     * Groups the observations by key and aggregates every metric of each group: a sum for counts, an average for the
     * percentage metrics. Groups come back sorted by key.
     */
    public static List<Group> groups(Collection<Observation> observations, Function<Observation, String> keyOf) {
        Map<String, List<Observation>> byKey = new TreeMap<>();
        for (Observation observation : observations) {
            byKey.computeIfAbsent(keyOf.apply(observation), key -> new ArrayList<>()).add(observation);
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<Observation>> entry : byKey.entrySet()) {
            groups.add(group(entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    /** The aggregate of all observations as one group under the given key. */
    public static Group total(String key, Collection<Observation> observations) {
        return group(key, List.copyOf(observations));
    }

    private static Group group(String key, List<Observation> observations) {
        Set<UUID> posts = new HashSet<>();
        for (Observation observation : observations) {
            posts.add(observation.postId());
        }
        List<Total> totals = new ArrayList<>();
        for (String metric : Metrics.ALL) {
            List<Long> values = new ArrayList<>();
            for (Observation observation : observations) {
                if (metric.equals(observation.metric()) && observation.value() != null) {
                    values.add(observation.value());
                }
            }
            boolean average = Metrics.isAverage(metric);
            Double value = null;
            if (!values.isEmpty()) {
                double sum = 0;
                for (long v : values) {
                    sum += v;
                }
                value = average ? sum / values.size() : sum;
            }
            totals.add(new Total(metric, value, values.size(), average));
        }
        return new Group(key, posts.size(), totals);
    }

    /**
     * The median of one metric per slot at equal age. Every slot from {@code first} to {@code last} is reported, with a
     * count of zero and a null median when no post of the slot has a value. Observations are expected to be snapshots at
     * one offset, already limited to posts old enough to have reached it.
     */
    public static List<Slot> medianBySlot(Collection<Observation> observations, String metric, int first, int last,
                                          ToIntFunction<Observation> slotOf) {
        Map<Integer, List<Long>> bySlot = new TreeMap<>();
        for (Observation observation : observations) {
            if (metric.equals(observation.metric()) && observation.value() != null) {
                bySlot.computeIfAbsent(slotOf.applyAsInt(observation), slot -> new ArrayList<>())
                        .add(observation.value());
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
