package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.Rollups.SnapshotInput;
import net.onelitefeather.menura.core.analytics.Rollups.Input;
import net.onelitefeather.menura.core.post.ExternalPost;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence of metric points, the latest values, snapshots, the rollups and the collection status (change
 * add-content-analytics). Every method runs on the connection of the transaction the caller opened.
 */
public interface MetricStore {

    /** Creates the month partitions of the given month and the next one, before any point of them is written. */
    void ensurePartitions(Connection connection, Instant now) throws SQLException;

    /** The offsets, in minutes, already snapshotted for each post of a channel. */
    Map<UUID, Set<Long>> takenOffsetMinutes(Connection connection, UUID channelId) throws SQLException;

    /** The series of each post of a channel that has one, as the assignment stands now. */
    Map<UUID, UUID> seriesOfPosts(Connection connection, UUID channelId) throws SQLException;

    /**
     * Stores the points of one reading, replaces the latest values of the post and marks the day of its publication for a
     * rollup rebuild.
     */
    void storeReading(Connection connection, Reading reading) throws SQLException;

    /**
     * Stores one point of an internal source and marks its day for a rollup rebuild. The caller has checked the source and
     * the metric against the registration.
     */
    void storeInternal(Connection connection, InternalPoint point, Instant collectedAt) throws SQLException;

    /**
     * Stores snapshots that are not stored yet. A snapshot that exists keeps its value.
     *
     * <p>Overwriting a snapshot when the platform revises its value is not done here yet: the port reports one value per
     * metric per read, not a value for each point in time, so there is no revised value per offset to store. Once an
     * adapter reports values per point in time, the conflict rule of this method changes to overwrite the snapshot of the
     * revised offset, while the raw points keep every collected value.
     */
    void storeSnapshots(Connection connection, List<SnapshotValue> snapshots) throws SQLException;

    /** When each post of a channel was last collected, from the latest values. A post never collected is absent. */
    Map<UUID, Instant> lastCollectedAt(Connection connection, UUID channelId) throws SQLException;

    void saveStatus(Connection connection, CollectionStatus status) throws SQLException;

    Optional<CollectionStatus> findStatus(Connection connection, UUID channelId) throws SQLException;

    List<CollectionStatus> statuses(Connection connection) throws SQLException;

    /** The days whose rollups are waiting for a rebuild, oldest first. */
    List<LocalDate> dirtyDays(Connection connection) throws SQLException;

    /**
     * Locks the dirty mark of a day until the transaction ends, so a collection that marks the day again waits for the
     * rebuild. Returns false when the day is not dirty (any more).
     */
    boolean lockDirtyDay(Connection connection, LocalDate day) throws SQLException;

    /** Removes the dirty mark of a day after its rollups were rebuilt. */
    void clearDirtyDay(Connection connection, LocalDate day) throws SQLException;

    /** The latest values of the posts published on the day, with their grain. */
    List<Input> platformInputs(Connection connection, LocalDate day) throws SQLException;

    /** The current point of every internal metric of the day, per source, scope, estimate flag and metric. */
    List<Input> internalInputs(Connection connection, LocalDate day) throws SQLException;

    /** The snapshots of the posts published on the day, with the publish hour and weekday of each post. */
    List<SnapshotInput> snapshotInputs(Connection connection, LocalDate day) throws SQLException;

    /** Replaces every daily row of the day with the given rows. */
    void replaceDay(Connection connection, LocalDate day, List<Rollup> rows) throws SQLException;

    /** Replaces the equal-age groups of the day with the given groups. */
    void replaceOffsets(Connection connection, LocalDate day, List<OffsetGroup> groups) throws SQLException;

    /** Replaces the weekly rows of the week that starts on the given Monday. */
    void replaceWeek(Connection connection, LocalDate weekStart, List<Rollup> rows) throws SQLException;

    /**
     * The daily rows of the days from {@code from} to {@code to}, both included. With roles, rows of series the roles may not
     * see are left out; without roles (null) every row is returned, as the rollup job reads them.
     */
    List<Rollup> dailyRows(Connection connection, LocalDate from, LocalDate to, Set<String> roles) throws SQLException;

    /** The weekly rows of the weeks that start from {@code from} to {@code to}, both included; roles as in {@link #dailyRows}. */
    List<Rollup> weeklyRows(Connection connection, LocalDate from, LocalDate to, Set<String> roles) throws SQLException;

    /** The equal-age groups of one offset and metric; optionally of one channel. */
    List<OffsetGroup> offsetGroups(Connection connection, Duration offset, String metric, UUID channelId) throws SQLException;

    /** The latest value of each metric of one post. */
    List<PointValue> latestOf(Connection connection, UUID postId) throws SQLException;

    List<SnapshotValue> snapshotsOf(Connection connection, UUID postId) throws SQLException;

    /** The raw points of one post and metric in collection-time order, at most {@code limit}. */
    List<PointValue> points(Connection connection, UUID postId, String metric, int limit) throws SQLException;

    /**
     * One collection of one post: the metrics the platform provided at the time, keyed by metric name. A metric that is
     * missing from the map is unavailable for the post. The render dimensions are absent for an imported post, and for a
     * post whose render was not recorded.
     */
    record Reading(ExternalPost post, Map<String, Long> values, UUID seriesId, Instant collectedAt,
                   String layoutTemplate, Integer brandKitVersion) {

        public Reading(ExternalPost post, Map<String, Long> values, UUID seriesId, Instant collectedAt) {
            this(post, values, seriesId, collectedAt, null, null);
        }
    }

    /**
     * One point of an internal metric source. Exactly one scope applies: the installation (no ids), a channel, an external
     * post (with its channel) or a series. {@code day} is the day the value refers to, in the installation time zone.
     */
    record InternalPoint(String source, String metric, UUID channelId, UUID postId, UUID seriesId, LocalDate day, long value,
                         boolean estimate) {
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
