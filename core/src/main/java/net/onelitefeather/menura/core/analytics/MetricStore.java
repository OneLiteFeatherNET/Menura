package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.post.ExternalPost;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence of metric points, the latest values, snapshots and the collection status (change add-content-analytics).
 * Every method runs on the connection of the transaction the caller opened.
 */
public interface MetricStore {

    /** Creates the month partitions of the given month and the next one, before any point of them is written. */
    void ensurePartitions(Connection connection, Instant now) throws SQLException;

    /** The offsets, in minutes, already snapshotted for each post of a channel. */
    Map<UUID, Set<Long>> takenOffsetMinutes(Connection connection, UUID channelId) throws SQLException;

    /** The series of each post of a channel that has one, as the assignment stands now. */
    Map<UUID, UUID> seriesOfPosts(Connection connection, UUID channelId) throws SQLException;

    /** Stores the points of one reading and replaces the latest values of the post. */
    void storeReading(Connection connection, Reading reading) throws SQLException;

    /** Stores snapshots that are not stored yet. A snapshot that exists keeps its value. */
    void storeSnapshots(Connection connection, List<SnapshotValue> snapshots) throws SQLException;

    void saveStatus(Connection connection, CollectionStatus status) throws SQLException;

    Optional<CollectionStatus> findStatus(Connection connection, UUID channelId) throws SQLException;

    List<CollectionStatus> statuses(Connection connection) throws SQLException;

    /**
     * The latest value of every post and metric published within the period, without the posts of series the roles may
     * not see.
     */
    List<Observation> latest(Connection connection, Instant from, Instant to, Set<String> roles) throws SQLException;

    /** The snapshots at one offset and metric, for the equal-age comparison; optionally of one channel. */
    List<Observation> snapshots(Connection connection, Duration offset, String metric, UUID channelId) throws SQLException;

    /** The latest value of each metric of one post. */
    List<PointValue> latestOf(Connection connection, UUID postId) throws SQLException;

    List<SnapshotValue> snapshotsOf(Connection connection, UUID postId) throws SQLException;

    /** The raw points of one post and metric in collection-time order, at most {@code limit}. */
    List<PointValue> points(Connection connection, UUID postId, String metric, int limit) throws SQLException;

    /**
     * One collection of one post: the metrics the platform provided at the time, keyed by metric name. A metric that is
     * missing from the map is unavailable for the post.
     */
    record Reading(ExternalPost post, Map<String, Long> values, UUID seriesId, Instant collectedAt) {
    }

    /** One snapshot: the value of a metric at an offset, or null when the platform did not provide it. */
    record SnapshotValue(UUID postId, int offsetMinutes, String metric, Long value, Instant asOf, int ageMinutes) {
    }

    /** A stored value with the time it was collected. */
    record PointValue(String metric, long value, Instant collectedAt) {
    }

    /** The outcome of the last collection of a channel. The failure reason is fixed text and holds no response body. */
    record CollectionStatus(UUID channelId, String outcome, Instant lastSuccessAt, Instant lastFailureAt,
                            String failureReason) {
    }
}
