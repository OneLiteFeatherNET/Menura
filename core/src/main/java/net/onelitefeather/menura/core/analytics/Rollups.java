package net.onelitefeather.menura.core.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The pure rules of the rollups (change add-content-analytics): the daily rows of a day, the weekly rows summed from the
 * daily ones, and the per-offset groups of the equal-age comparison. No I/O, so the rules are tested without a database;
 * the rollup job feeds them the rows it reads and stores what they return.
 *
 * <p>A rollup sums counts. A metric no post provides is a row with no values, never a row of zeros. An average (the
 * percentage) is the sum divided by the number of values, read in {@link Aggregation}.
 */
public final class Rollups {

    /** The source of the platform metrics of external posts; every other source is an internal metric source. */
    public static final String PLATFORM = "platform";

    private Rollups() {
    }

    /**
     * The dimensions a rollup row groups by. A null dimension is one the value does not have: an internal installation
     * value has no channel, a value of a post in no series has no series, an imported post has no layout template.
     */
    public record Grain(UUID channelId, String format, Integer publishHour, UUID seriesId, String layoutTemplate,
                        Integer brandKitVersion, String source, boolean estimate) {

        /** The grain of a platform value. */
        public static Grain platform(UUID channelId, String format, int publishHour, UUID seriesId, String layoutTemplate,
                                     Integer brandKitVersion) {
            return new Grain(channelId, format, publishHour, seriesId, layoutTemplate, brandKitVersion, PLATFORM, false);
        }

        public boolean platformValue() {
            return PLATFORM.equals(source);
        }
    }

    /**
     * One value a daily rollup sums: a latest value of an external post, or the current point of an internal source.
     *
     * @param postId the post the value belongs to, or null for an installation or channel value
     */
    public record Input(UUID postId, Grain grain, String metric, Long value) {
    }

    /**
     * One row of a rollup: the sum of the values of one grain and metric in one period. {@code period} is the day, or the
     * Monday of the week in the weekly rollup. {@code posts} counts the distinct posts of the grain; {@code valuesCount} the
     * values of this metric that were present.
     */
    public record Rollup(LocalDate period, Grain grain, String metric, int posts, int valuesCount, long total) {
    }

    /** A snapshot of one post at one offset, with the publish hour and weekday the post belongs to. */
    public record SnapshotInput(UUID postId, UUID channelId, int publishHour, int publishWeekday, int offsetMinutes,
                                String metric, Long value) {
    }

    /** The snapshot values of one channel, hour, weekday, offset and metric on one day; posts without a value are absent. */
    public record OffsetGroup(LocalDate day, UUID channelId, int publishHour, int publishWeekday, int offsetMinutes,
                              String metric, List<Long> values) {
    }

    /**
     * The daily rows of one day. Every platform grain gets one row per metric of {@link Metrics#ALL}, so its post count is
     * the same on every row; an internal grain gets one row per metric it carries.
     */
    public static List<Rollup> daily(LocalDate day, Collection<Input> inputs) {
        Map<Grain, List<Input>> byGrain = new LinkedHashMap<>();
        for (Input input : inputs) {
            byGrain.computeIfAbsent(input.grain(), grain -> new ArrayList<>()).add(input);
        }
        List<Rollup> rows = new ArrayList<>();
        for (Map.Entry<Grain, List<Input>> entry : byGrain.entrySet()) {
            Grain grain = entry.getKey();
            List<Input> own = entry.getValue();
            Set<UUID> posts = new HashSet<>();
            Set<String> metrics = new TreeSet<>();
            for (Input input : own) {
                if (input.postId() != null) {
                    posts.add(input.postId());
                }
                metrics.add(input.metric());
            }
            Collection<String> names = grain.platformValue() ? Metrics.ALL : metrics;
            for (String metric : names) {
                int count = 0;
                long total = 0;
                for (Input input : own) {
                    if (metric.equals(input.metric()) && input.value() != null) {
                        count++;
                        total += input.value();
                    }
                }
                rows.add(new Rollup(day, grain, metric, posts.size(), count, total));
            }
        }
        return rows;
    }

    /**
     * The weekly rows of one week, summed from the daily rows of that week. A post belongs to the day it was published on,
     * so the post counts of the days add up to the distinct posts of the week.
     */
    public static List<Rollup> weekly(LocalDate weekStart, Collection<Rollup> daily) {
        Map<Cell, long[]> sums = new LinkedHashMap<>();
        for (Rollup row : daily) {
            long[] sum = sums.computeIfAbsent(new Cell(row.grain(), row.metric()), cell -> new long[3]);
            sum[0] += row.posts();
            sum[1] += row.valuesCount();
            sum[2] += row.total();
        }
        List<Rollup> rows = new ArrayList<>();
        for (Map.Entry<Cell, long[]> entry : sums.entrySet()) {
            long[] sum = entry.getValue();
            rows.add(new Rollup(weekStart, entry.getKey().grain(), entry.getKey().metric(), (int) sum[0], (int) sum[1],
                    sum[2]));
        }
        return rows;
    }

    /**
     * The equal-age groups of one day: the snapshot values of the posts published that day, grouped by channel, publish
     * hour, weekday, offset and metric. A group with no value is not returned.
     */
    public static List<OffsetGroup> offsets(LocalDate day, Collection<SnapshotInput> snapshots) {
        Map<OffsetKey, List<Long>> groups = new LinkedHashMap<>();
        for (SnapshotInput snapshot : snapshots) {
            OffsetKey key = new OffsetKey(snapshot.channelId(), snapshot.publishHour(), snapshot.publishWeekday(),
                    snapshot.offsetMinutes(), snapshot.metric());
            List<Long> values = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (snapshot.value() != null) {
                values.add(snapshot.value());
            }
        }
        List<OffsetGroup> rows = new ArrayList<>();
        for (Map.Entry<OffsetKey, List<Long>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            OffsetKey key = entry.getKey();
            rows.add(new OffsetGroup(day, key.channelId(), key.publishHour(), key.publishWeekday(), key.offsetMinutes(),
                    key.metric(), List.copyOf(entry.getValue())));
        }
        return rows;
    }

    private record Cell(Grain grain, String metric) {
    }

    private record OffsetKey(UUID channelId, int publishHour, int publishWeekday, int offsetMinutes, String metric) {
    }
}
