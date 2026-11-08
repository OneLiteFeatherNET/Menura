package net.onelitefeather.menura.core.analytics.support;

import net.onelitefeather.menura.core.analytics.MetricStore;
import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.analytics.Metrics;
import net.onelitefeather.menura.core.analytics.Observation;
import net.onelitefeather.menura.core.post.ExternalPost;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An in-memory {@link MetricStore} for the unit tests. It refuses a point whose month has no partition, as PostgreSQL
 * does, and it keeps no state outside its instance.
 */
public final class InMemoryMetricStore implements MetricStore {

    private final Set<YearMonth> partitions = new HashSet<>();
    private final List<Point> points = new ArrayList<>();
    private final Map<String, Observation> latest = new LinkedHashMap<>();
    private final Map<String, Instant> latestCollectedAt = new HashMap<>();
    private final Map<String, SnapshotValue> snapshots = new LinkedHashMap<>();
    private final Map<UUID, ExternalPost> seen = new HashMap<>();
    private final Map<UUID, UUID> assignments = new HashMap<>();
    private final Map<UUID, CollectionStatus> statuses = new HashMap<>();

    /** A stored point, as the partitioned table holds it. */
    public record Point(UUID postId, String metric, long value, Instant collectedAt, UUID seriesId) {
    }

    /** Assigns a post to a series, as the series assignment does; the next reading carries it. */
    public void assign(UUID postId, UUID seriesId) {
        assignments.put(postId, seriesId);
    }

    /** Every stored point in insertion order. */
    public List<Point> points() {
        return List.copyOf(points);
    }

    public Optional<CollectionStatus> status(UUID channelId) {
        return Optional.ofNullable(statuses.get(channelId));
    }

    public boolean hasPartition(YearMonth month) {
        return partitions.contains(month);
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
        YearMonth month = YearMonth.from(reading.collectedAt().atOffset(ZoneOffset.UTC));
        if (!partitions.contains(month)) {
            throw new SQLException("no partition of metric_point exists for " + month);
        }
        ExternalPost post = reading.post();
        seen.put(post.id(), post);
        latest.keySet().removeIf(key -> key.startsWith(post.id() + "|"));
        for (Map.Entry<String, Long> entry : reading.values().entrySet()) {
            if (!Metrics.isKnown(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            points.add(new Point(post.id(), entry.getKey(), entry.getValue(), reading.collectedAt(), reading.seriesId()));
            Observation observation = new Observation(post.id(), post.channelId(), post.platform(), post.format(),
                    reading.seriesId(), post.publishedAt(), entry.getKey(), entry.getValue());
            latest.put(post.id() + "|" + entry.getKey(), observation);
            latestCollectedAt.put(post.id() + "|" + entry.getKey(), reading.collectedAt());
        }
    }

    @Override
    public void storeSnapshots(Connection connection, List<SnapshotValue> incoming) {
        for (SnapshotValue snapshot : incoming) {
            snapshots.putIfAbsent(snapshot.postId() + "|" + snapshot.offsetMinutes() + "|" + snapshot.metric(), snapshot);
        }
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
    public List<Observation> latest(Connection connection, Instant from, Instant to, Set<String> roles) {
        return latest.values().stream()
                .filter(o -> !o.publishedAt().isBefore(from) && o.publishedAt().isBefore(to))
                .collect(Collectors.toList());
    }

    @Override
    public List<Observation> snapshots(Connection connection, Duration offset, String metric, UUID channelId) {
        List<Observation> observations = new ArrayList<>();
        for (SnapshotValue snapshot : snapshots.values()) {
            if (snapshot.offsetMinutes() != offset.toMinutes() || !snapshot.metric().equals(metric)) {
                continue;
            }
            ExternalPost post = seen.get(snapshot.postId());
            if (channelId != null && !channelId.equals(post.channelId())) {
                continue;
            }
            observations.add(new Observation(post.id(), post.channelId(), post.platform(), post.format(), null,
                    post.publishedAt(), metric, snapshot.value()));
        }
        return observations;
    }

    @Override
    public List<PointValue> latestOf(Connection connection, UUID postId) {
        List<PointValue> values = new ArrayList<>();
        for (Map.Entry<String, Observation> entry : latest.entrySet()) {
            if (entry.getValue().postId().equals(postId)) {
                String key = entry.getKey();
                values.add(new PointValue(entry.getValue().metric(), entry.getValue().value(), latestCollectedAt.get(key)));
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

    private UUID channelOf(UUID postId) {
        ExternalPost post = seen.get(postId);
        return post == null ? null : post.channelId();
    }
}
