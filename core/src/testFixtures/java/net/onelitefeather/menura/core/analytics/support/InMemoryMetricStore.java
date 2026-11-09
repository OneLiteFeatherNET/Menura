package net.onelitefeather.menura.core.analytics.support;

import net.onelitefeather.menura.core.analytics.MetricStore;
import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.InternalPoint;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.analytics.Metrics;
import net.onelitefeather.menura.core.analytics.PublishSlot;
import net.onelitefeather.menura.core.analytics.Rollups;
import net.onelitefeather.menura.core.analytics.Rollups.Grain;
import net.onelitefeather.menura.core.analytics.Rollups.Input;
import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.Rollups.SnapshotInput;
import net.onelitefeather.menura.core.post.ExternalPost;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An in-memory {@link MetricStore} for the unit tests. It refuses a point whose month has no partition, as PostgreSQL
 * does, keeps the rollups and dirty days the same way the tables do, and keeps no state outside its instance. Series
 * visibility is not modelled: every rollup row is returned to every role.
 */
public final class InMemoryMetricStore implements MetricStore {

    private final PublishSlot slots;
    private final Set<YearMonth> partitions = new HashSet<>();
    private final List<Point> points = new ArrayList<>();
    private final List<InternalRow> internal = new ArrayList<>();
    private final Map<String, Latest> latest = new LinkedHashMap<>();
    private final Map<String, SnapshotValue> snapshots = new LinkedHashMap<>();
    private final Map<UUID, ExternalPost> seen = new HashMap<>();
    private final Map<UUID, UUID> assignments = new HashMap<>();
    private final Map<UUID, CollectionStatus> statuses = new HashMap<>();
    private final TreeSet<LocalDate> dirty = new TreeSet<>();
    private final List<Rollup> daily = new ArrayList<>();
    private final List<Rollup> weekly = new ArrayList<>();
    private final List<OffsetGroup> offsets = new ArrayList<>();

    /** A stored point, as the partitioned table holds it. */
    public record Point(UUID postId, String metric, long value, Instant collectedAt, UUID seriesId) {
    }

    /** A stored internal point, as the partitioned table holds it. */
    public record InternalRow(InternalPoint point, Instant collectedAt) {
    }

    /** The latest value of a post and metric with its grain. */
    private record Latest(ExternalPost post, String metric, long value, Instant collectedAt, UUID seriesId,
                          String layoutTemplate, Integer brandKitVersion) {
    }

    public InMemoryMetricStore() {
        this(new PublishSlot(ZoneId.of("Europe/Berlin")));
    }

    public InMemoryMetricStore(PublishSlot slots) {
        this.slots = slots;
    }

    /** Forgets every row, partition and dirty day, so a test starts from an empty store. */
    public void clear() {
        partitions.clear();
        points.clear();
        internal.clear();
        latest.clear();
        snapshots.clear();
        seen.clear();
        assignments.clear();
        statuses.clear();
        dirty.clear();
        daily.clear();
        weekly.clear();
        offsets.clear();
    }

    /** Assigns a post to a series, as the series assignment does; the next reading carries it. */
    public void assign(UUID postId, UUID seriesId) {
        assignments.put(postId, seriesId);
    }

    /** Every stored point in insertion order. */
    public List<Point> points() {
        return List.copyOf(points);
    }

    /** Every stored internal point in insertion order. */
    public List<InternalRow> internalPoints() {
        return List.copyOf(internal);
    }

    public Optional<CollectionStatus> status(UUID channelId) {
        return Optional.ofNullable(statuses.get(channelId));
    }

    public boolean hasPartition(YearMonth month) {
        return partitions.contains(month);
    }

    /** The days whose rollups wait for a rebuild. */
    public Set<LocalDate> dirtyDays() {
        return Set.copyOf(dirty);
    }

    @Override
    public void ensurePartitions(Connection connection, Instant now) {
        YearMonth current = YearMonth.from(now.atOffset(ZoneOffset.UTC));
        partitions.add(current);
        partitions.add(current.plusMonths(1));
    }

    @Override
    public Map<UUID, Set<Long>> takenOffsetMinutes(Connection connection, UUID channelId) {
        Map<UUID, Set<Long>> taken = new HashMap<>();
        for (SnapshotValue snapshot : snapshots.values()) {
            if (channelId.equals(channelOf(snapshot.postId()))) {
                taken.computeIfAbsent(snapshot.postId(), id -> new HashSet<>()).add((long) snapshot.offsetMinutes());
            }
        }
        return taken;
    }

    @Override
    public Map<UUID, UUID> seriesOfPosts(Connection connection, UUID channelId) {
        // The double keeps assignments without their channel; the tests that use it hold one channel per store.
        return new HashMap<>(assignments);
    }

    @Override
    public void storeReading(Connection connection, Reading reading) throws SQLException {
        requirePartition(reading.collectedAt());
        ExternalPost post = reading.post();
        seen.put(post.id(), post);
        latest.keySet().removeIf(key -> key.startsWith(post.id() + "|"));
        for (Map.Entry<String, Long> entry : reading.values().entrySet()) {
            if (!Metrics.isKnown(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            points.add(new Point(post.id(), entry.getKey(), entry.getValue(), reading.collectedAt(), reading.seriesId()));
            latest.put(post.id() + "|" + entry.getKey(), new Latest(post, entry.getKey(), entry.getValue(),
                    reading.collectedAt(), reading.seriesId(), reading.layoutTemplate(), reading.brandKitVersion()));
        }
        dirty.add(slots.day(post.publishedAt()));
    }

    @Override
    public void storeInternal(Connection connection, InternalPoint point, Instant collectedAt) throws SQLException {
        requirePartition(collectedAt);
        internal.add(new InternalRow(point, collectedAt));
        dirty.add(point.day());
    }

    @Override
    public void storeSnapshots(Connection connection, List<SnapshotValue> incoming) {
        for (SnapshotValue snapshot : incoming) {
            snapshots.putIfAbsent(snapshot.postId() + "|" + snapshot.offsetMinutes() + "|" + snapshot.metric(), snapshot);
        }
    }

    @Override
    public Map<UUID, Instant> lastCollectedAt(Connection connection, UUID channelId) {
        Map<UUID, Instant> last = new HashMap<>();
        for (Latest value : latest.values()) {
            if (channelId.equals(value.post().channelId())) {
                last.merge(value.post().id(), value.collectedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return last;
    }

    @Override
    public void saveStatus(Connection connection, CollectionStatus status) {
        statuses.put(status.channelId(), status);
    }

    @Override
    public Optional<CollectionStatus> findStatus(Connection connection, UUID channelId) {
        return status(channelId);
    }

    @Override
    public List<CollectionStatus> statuses(Connection connection) {
        return List.copyOf(statuses.values());
    }

    @Override
    public List<LocalDate> dirtyDays(Connection connection) {
        return List.copyOf(dirty);
    }

    @Override
    public boolean lockDirtyDay(Connection connection, LocalDate day) {
        return dirty.contains(day);
    }

    @Override
    public void clearDirtyDay(Connection connection, LocalDate day) {
        dirty.remove(day);
    }

    @Override
    public List<Input> platformInputs(Connection connection, LocalDate day) {
        List<Input> inputs = new ArrayList<>();
        for (Latest value : latest.values()) {
            if (!slots.day(value.post().publishedAt()).equals(day)) {
                continue;
            }
            ExternalPost post = value.post();
            Grain grain = Grain.platform(post.channelId(), post.format(), slots.hour(post.publishedAt()), value.seriesId(),
                    value.layoutTemplate(), value.brandKitVersion());
            inputs.add(new Input(post.id(), grain, value.metric(), value.value()));
        }
        return inputs;
    }

    @Override
    public List<Input> internalInputs(Connection connection, LocalDate day) {
        // The latest collected point per source, metric, scope and estimate flag, as the table's distinct-on key keeps it.
        Map<List<Object>, InternalRow> current = new LinkedHashMap<>();
        for (InternalRow row : internal) {
            InternalPoint point = row.point();
            if (!point.day().equals(day)) {
                continue;
            }
            List<Object> key = List.of(point.source(), point.metric(), String.valueOf(point.channelId()),
                    String.valueOf(point.postId()), String.valueOf(point.seriesId()), point.estimate());
            InternalRow kept = current.get(key);
            if (kept == null || !row.collectedAt().isBefore(kept.collectedAt())) {
                current.put(key, row);
            }
        }
        List<Input> inputs = new ArrayList<>();
        for (InternalRow row : current.values()) {
            InternalPoint point = row.point();
            Grain grain = new Grain(point.channelId(), null, null, point.seriesId(), null, null, point.source(),
                    point.estimate());
            inputs.add(new Input(point.postId(), grain, point.metric(), point.value()));
        }
        return inputs;
    }

    @Override
    public List<SnapshotInput> snapshotInputs(Connection connection, LocalDate day) {
        List<SnapshotInput> inputs = new ArrayList<>();
        for (SnapshotValue snapshot : snapshots.values()) {
            ExternalPost post = seen.get(snapshot.postId());
            if (post == null || !slots.day(post.publishedAt()).equals(day)) {
                continue;
            }
            inputs.add(new SnapshotInput(post.id(), post.channelId(), slots.hour(post.publishedAt()),
                    slots.weekday(post.publishedAt()), snapshot.offsetMinutes(), snapshot.metric(), snapshot.value()));
        }
        return inputs;
    }

    @Override
    public void replaceDay(Connection connection, LocalDate day, List<Rollup> rows) {
        daily.removeIf(row -> row.period().equals(day));
        daily.addAll(rows);
    }

    @Override
    public void replaceWeek(Connection connection, LocalDate weekStart, List<Rollup> rows) {
        weekly.removeIf(row -> row.period().equals(weekStart));
        weekly.addAll(rows);
    }

    @Override
    public void replaceOffsets(Connection connection, LocalDate day, List<OffsetGroup> groups) {
        offsets.removeIf(group -> group.day().equals(day));
        offsets.addAll(groups);
    }

    @Override
    public List<Rollup> dailyRows(Connection connection, LocalDate from, LocalDate to, Set<String> roles) {
        return daily.stream().filter(row -> !row.period().isBefore(from) && !row.period().isAfter(to)).toList();
    }

    @Override
    public List<Rollup> weeklyRows(Connection connection, LocalDate from, LocalDate to, Set<String> roles) {
        return weekly.stream().filter(row -> !row.period().isBefore(from) && !row.period().isAfter(to)).toList();
    }

    @Override
    public List<OffsetGroup> offsetGroups(Connection connection, Duration offset, String metric, UUID channelId) {
        return offsets.stream()
                .filter(group -> group.offsetMinutes() == offset.toMinutes() && group.metric().equals(metric))
                .filter(group -> channelId == null || channelId.equals(group.channelId()))
                .toList();
    }

    @Override
    public List<PointValue> latestOf(Connection connection, UUID postId) {
        List<PointValue> values = new ArrayList<>();
        for (Latest value : latest.values()) {
            if (value.post().id().equals(postId)) {
                values.add(new PointValue(value.metric(), value.value(), value.collectedAt()));
            }
        }
        return values;
    }

    @Override
    public List<SnapshotValue> snapshotsOf(Connection connection, UUID postId) {
        return snapshots.values().stream().filter(s -> s.postId().equals(postId)).toList();
    }

    @Override
    public List<PointValue> points(Connection connection, UUID postId, String metric, int limit) {
        return points.stream()
                .filter(p -> p.postId().equals(postId) && (metric == null || metric.equals(p.metric())))
                .limit(limit)
                .map(p -> new PointValue(p.metric(), p.value(), p.collectedAt()))
                .toList();
    }

    private void requirePartition(Instant collectedAt) throws SQLException {
        YearMonth month = YearMonth.from(collectedAt.atOffset(ZoneOffset.UTC));
        if (!partitions.contains(month)) {
            throw new SQLException("no partition of metric_point exists for " + month);
        }
    }

    private UUID channelOf(UUID postId) {
        ExternalPost post = seen.get(postId);
        return post == null ? null : post.channelId();
    }
}
