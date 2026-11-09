package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.InternalPoint;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.analytics.Rollups.Grain;
import net.onelitefeather.menura.core.analytics.Rollups.Input;
import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.Rollups.SnapshotInput;
import net.onelitefeather.menura.core.id.IdentifierGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL implementation of {@link MetricStore} over the tables of migrations V10 and V15. */
public final class JdbcMetricStore implements MetricStore {

    /** The stand-in of a null scope id in the distinct-on key of the internal points; no row has it. */
    private static final String NO_ID = "00000000-0000-0000-0000-000000000000";

    private final IdentifierGenerator identifiers;
    private final PublishSlot slots;

    public JdbcMetricStore(IdentifierGenerator identifiers, PublishSlot slots) {
        this.identifiers = identifiers;
        this.slots = slots;
    }

    @Override
    public Map<UUID, Instant> lastCollectedAt(Connection connection, UUID channelId) throws SQLException {
        Map<UUID, Instant> last = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select external_post_id, max(collected_at) from menura.metric_latest where channel_id = ? "
                        + "group by external_post_id")) {
            statement.setObject(1, channelId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    last.put(row.getObject(1, UUID.class), row.getTimestamp(2).toInstant());
                }
            }
        }
        return last;
    }

    @Override
    public void ensurePartitions(Connection connection, Instant now) throws SQLException {
        YearMonth current = YearMonth.from(now.atOffset(ZoneOffset.UTC));
        try (Statement statement = connection.createStatement()) {
            for (YearMonth month : List.of(current, current.plusMonths(1))) {
                // The bounds are integers formatted here, never user input.
                statement.execute("create table if not exists menura.metric_point_" + month.getYear() + "_"
                        + String.format("%02d", month.getMonthValue()) + " partition of menura.metric_point for values from ('"
                        + month.atDay(1) + " 00:00:00+00') to ('" + month.plusMonths(1).atDay(1) + " 00:00:00+00')");
            }
        }
    }

    @Override
    public Map<UUID, Set<Long>> takenOffsetMinutes(Connection connection, UUID channelId) throws SQLException {
        Map<UUID, Set<Long>> taken = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select distinct s.external_post_id, s.offset_minutes from menura.metric_snapshot s "
                        + "join menura.external_post p on p.id = s.external_post_id where p.channel_id = ?")) {
            statement.setObject(1, channelId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    taken.computeIfAbsent(row.getObject(1, UUID.class), id -> new HashSet<>())
                            .add((long) row.getInt(2));
                }
            }
        }
        return taken;
    }

    @Override
    public Map<UUID, UUID> seriesOfPosts(Connection connection, UUID channelId) throws SQLException {
        Map<UUID, UUID> series = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select a.post_id, a.series_id from menura.content_series_assignment a "
                        + "join menura.external_post p on p.id = a.post_id "
                        + "where p.channel_id = ? and a.series_id is not null")) {
            statement.setObject(1, channelId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    series.put(row.getObject(1, UUID.class), row.getObject(2, UUID.class));
                }
            }
        }
        return series;
    }

    @Override
    public void storeReading(Connection connection, Reading reading) throws SQLException {
        UUID postId = reading.post().id();
        int hour = slots.hour(reading.post().publishedAt());
        int weekday = slots.weekday(reading.post().publishedAt());
        try (PreparedStatement delete = connection.prepareStatement(
                "delete from menura.metric_latest where external_post_id = ?")) {
            delete.setObject(1, postId);
            delete.executeUpdate();
        }
        try (PreparedStatement points = connection.prepareStatement(
                "insert into menura.metric_point (id, external_post_id, channel_id, platform, format, publish_hour, "
                        + "publish_weekday, series_id, layout_template, brand_kit_version, metric, value, collected_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement latest = connection.prepareStatement(
                     "insert into menura.metric_latest (external_post_id, metric, channel_id, platform, format, "
                             + "published_at, publish_hour, publish_weekday, series_id, layout_template, brand_kit_version, "
                             + "value, collected_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Map.Entry<String, Long> entry : reading.values().entrySet()) {
                if (!Metrics.isKnown(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                int index = 1;
                points.setObject(index++, identifiers.next());
                points.setObject(index++, postId);
                points.setObject(index++, reading.post().channelId());
                points.setString(index++, reading.post().platform());
                points.setString(index++, reading.post().format());
                points.setInt(index++, hour);
                points.setInt(index++, weekday);
                points.setObject(index++, reading.seriesId());
                points.setString(index++, reading.layoutTemplate());
                setNullableInt(points, index++, reading.brandKitVersion());
                points.setString(index++, entry.getKey());
                points.setLong(index++, entry.getValue());
                points.setTimestamp(index, Timestamp.from(reading.collectedAt()));
                points.addBatch();

                index = 1;
                latest.setObject(index++, postId);
                latest.setString(index++, entry.getKey());
                latest.setObject(index++, reading.post().channelId());
                latest.setString(index++, reading.post().platform());
                latest.setString(index++, reading.post().format());
                latest.setTimestamp(index++, Timestamp.from(reading.post().publishedAt()));
                latest.setInt(index++, hour);
                latest.setInt(index++, weekday);
                latest.setObject(index++, reading.seriesId());
                latest.setString(index++, reading.layoutTemplate());
                setNullableInt(latest, index++, reading.brandKitVersion());
                latest.setLong(index++, entry.getValue());
                latest.setTimestamp(index, Timestamp.from(reading.collectedAt()));
                latest.addBatch();
            }
            points.executeBatch();
            latest.executeBatch();
        }
        markDirty(connection, slots.day(reading.post().publishedAt()));
    }

    @Override
    public void storeInternal(Connection connection, InternalPoint point, Instant collectedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.metric_point (id, external_post_id, channel_id, series_id, metric, value, collected_at, "
                        + "source, estimate, day) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, identifiers.next());
            statement.setObject(2, point.postId());
            statement.setObject(3, point.channelId());
            statement.setObject(4, point.seriesId());
            statement.setString(5, point.metric());
            statement.setLong(6, point.value());
            statement.setTimestamp(7, Timestamp.from(collectedAt));
            statement.setString(8, point.source());
            statement.setBoolean(9, point.estimate());
            statement.setObject(10, point.day());
            statement.executeUpdate();
        }
        markDirty(connection, point.day());
    }

    @Override
    public void storeSnapshots(Connection connection, List<SnapshotValue> snapshots) throws SQLException {
        if (snapshots.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.metric_snapshot (external_post_id, offset_minutes, metric, value, as_of, age_minutes) "
                        + "values (?, ?, ?, ?, ?, ?) on conflict do nothing")) {
            for (SnapshotValue snapshot : snapshots) {
                statement.setObject(1, snapshot.postId());
                statement.setInt(2, snapshot.offsetMinutes());
                statement.setString(3, snapshot.metric());
                if (snapshot.value() == null) {
                    statement.setNull(4, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(4, snapshot.value());
                }
                statement.setTimestamp(5, Timestamp.from(snapshot.asOf()));
                statement.setInt(6, snapshot.ageMinutes());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public void saveStatus(Connection connection, CollectionStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.metric_collection_status (channel_id, outcome, last_success_at, last_failure_at, "
                        + "failure_reason) values (?, ?, ?, ?, ?) on conflict (channel_id) do update set "
                        + "outcome = excluded.outcome, last_success_at = excluded.last_success_at, "
                        + "last_failure_at = excluded.last_failure_at, failure_reason = excluded.failure_reason")) {
            statement.setObject(1, status.channelId());
            statement.setString(2, status.outcome());
            statement.setTimestamp(3, timestamp(status.lastSuccessAt()));
            statement.setTimestamp(4, timestamp(status.lastFailureAt()));
            statement.setString(5, status.failureReason());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<CollectionStatus> findStatus(Connection connection, UUID channelId) throws SQLException {
        List<CollectionStatus> found = statusRows(connection, "where channel_id = ?", channelId);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public List<CollectionStatus> statuses(Connection connection) throws SQLException {
        return statusRows(connection, "", null);
    }

    @Override
    public List<LocalDate> dirtyDays(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select day from menura.metric_rollup_dirty order by day");
             ResultSet row = statement.executeQuery()) {
            List<LocalDate> days = new ArrayList<>();
            while (row.next()) {
                days.add(row.getObject(1, LocalDate.class));
            }
            return days;
        }
    }

    @Override
    public boolean lockDirtyDay(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select day from menura.metric_rollup_dirty where day = ? for update")) {
            statement.setObject(1, day);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    @Override
    public void clearDirtyDay(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from menura.metric_rollup_dirty where day = ?")) {
            statement.setObject(1, day);
            statement.executeUpdate();
        }
    }

    @Override
    public List<Input> platformInputs(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select external_post_id, channel_id, format, publish_hour, series_id, layout_template, brand_kit_version, "
                        + "metric, value from menura.metric_latest where published_at >= ? and published_at < ?")) {
            statement.setTimestamp(1, Timestamp.from(slots.startOf(day)));
            statement.setTimestamp(2, Timestamp.from(slots.startOf(day.plusDays(1))));
            try (ResultSet row = statement.executeQuery()) {
                List<Input> inputs = new ArrayList<>();
                while (row.next()) {
                    Grain grain = Grain.platform(row.getObject(2, UUID.class), row.getString(3), row.getInt(4),
                            row.getObject(5, UUID.class), row.getString(6), nullableInt(row, 7));
                    inputs.add(new Input(row.getObject(1, UUID.class), grain, row.getString(8), row.getLong(9)));
                }
                return inputs;
            }
        }
    }

    @Override
    public List<Input> internalInputs(Connection connection, LocalDate day) throws SQLException {
        // The current point of each internal value: the latest collected point per source, metric, scope and estimate flag.
        try (PreparedStatement statement = connection.prepareStatement(
                "select distinct on (source, metric, coalesce(channel_id, '" + NO_ID + "'), "
                        + "coalesce(external_post_id, '" + NO_ID + "'), coalesce(series_id, '" + NO_ID + "'), estimate) "
                        + "source, metric, channel_id, external_post_id, series_id, estimate, value "
                        + "from menura.metric_point where source <> 'platform' and day = ? "
                        + "order by source, metric, coalesce(channel_id, '" + NO_ID + "'), "
                        + "coalesce(external_post_id, '" + NO_ID + "'), coalesce(series_id, '" + NO_ID + "'), estimate, "
                        + "collected_at desc, id desc")) {
            statement.setObject(1, day);
            try (ResultSet row = statement.executeQuery()) {
                List<Input> inputs = new ArrayList<>();
                while (row.next()) {
                    Grain grain = new Grain(row.getObject(3, UUID.class), null, null, row.getObject(5, UUID.class), null,
                            null, row.getString(1), row.getBoolean(6));
                    inputs.add(new Input(row.getObject(4, UUID.class), grain, row.getString(2), row.getLong(7)));
                }
                return inputs;
            }
        }
    }

    @Override
    public List<SnapshotInput> snapshotInputs(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select s.external_post_id, p.channel_id, p.published_at, s.offset_minutes, s.metric, s.value "
                        + "from menura.metric_snapshot s join menura.external_post p on p.id = s.external_post_id "
                        + "where p.published_at >= ? and p.published_at < ?")) {
            statement.setTimestamp(1, Timestamp.from(slots.startOf(day)));
            statement.setTimestamp(2, Timestamp.from(slots.startOf(day.plusDays(1))));
            try (ResultSet row = statement.executeQuery()) {
                List<SnapshotInput> inputs = new ArrayList<>();
                while (row.next()) {
                    Instant publishedAt = row.getTimestamp(3).toInstant();
                    inputs.add(new SnapshotInput(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                            slots.hour(publishedAt), slots.weekday(publishedAt), row.getInt(4), row.getString(5),
                            nullableLong(row, 6)));
                }
                return inputs;
            }
        }
    }

    @Override
    public void replaceDay(Connection connection, LocalDate day, List<Rollup> rows) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("delete from menura.metric_rollup_day where day = ?")) {
            delete.setObject(1, day);
            delete.executeUpdate();
        }
        insertRollups(connection, "menura.metric_rollup_day", "day", rows);
    }

    @Override
    public void replaceWeek(Connection connection, LocalDate weekStart, List<Rollup> rows) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "delete from menura.metric_rollup_week where week_start = ?")) {
            delete.setObject(1, weekStart);
            delete.executeUpdate();
        }
        insertRollups(connection, "menura.metric_rollup_week", "week_start", rows);
    }

    @Override
    public void replaceOffsets(Connection connection, LocalDate day, List<OffsetGroup> groups) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("delete from menura.metric_offset_day where day = ?")) {
            delete.setObject(1, day);
            delete.executeUpdate();
        }
        if (groups.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into menura.metric_offset_day (day, channel_id, publish_hour, publish_weekday, offset_minutes, "
                        + "metric, snapshot_values) values (?, ?, ?, ?, ?, ?, ?)")) {
            for (OffsetGroup group : groups) {
                insert.setObject(1, group.day());
                insert.setObject(2, group.channelId());
                insert.setInt(3, group.publishHour());
                insert.setInt(4, group.publishWeekday());
                insert.setInt(5, group.offsetMinutes());
                insert.setString(6, group.metric());
                insert.setArray(7, connection.createArrayOf("bigint", group.values().toArray()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    @Override
    public List<Rollup> dailyRows(Connection connection, LocalDate from, LocalDate to, Set<String> roles)
            throws SQLException {
        return rollupRows(connection, "menura.metric_rollup_day", "day", from, to, roles);
    }

    @Override
    public List<Rollup> weeklyRows(Connection connection, LocalDate from, LocalDate to, Set<String> roles)
            throws SQLException {
        return rollupRows(connection, "menura.metric_rollup_week", "week_start", from, to, roles);
    }

    @Override
    public List<OffsetGroup> offsetGroups(Connection connection, Duration offset, String metric, UUID channelId)
            throws SQLException {
        String sql = "select day, channel_id, publish_hour, publish_weekday, offset_minutes, metric, snapshot_values "
                + "from menura.metric_offset_day where offset_minutes = ? and metric = ?"
                + (channelId == null ? "" : " and channel_id = ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, (int) offset.toMinutes());
            statement.setString(2, metric);
            if (channelId != null) {
                statement.setObject(3, channelId);
            }
            try (ResultSet row = statement.executeQuery()) {
                List<OffsetGroup> groups = new ArrayList<>();
                while (row.next()) {
                    List<Long> values = new ArrayList<>();
                    for (Object value : (Object[]) row.getArray(7).getArray()) {
                        values.add((Long) value);
                    }
                    groups.add(new OffsetGroup(row.getObject(1, LocalDate.class), row.getObject(2, UUID.class),
                            row.getInt(3), row.getInt(4), row.getInt(5), row.getString(6), values));
                }
                return groups;
            }
        }
    }

    @Override
    public List<PointValue> latestOf(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select metric, value, collected_at from menura.metric_latest where external_post_id = ? order by metric")) {
            statement.setObject(1, postId);
            return pointValues(statement);
        }
    }

    @Override
    public List<SnapshotValue> snapshotsOf(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select offset_minutes, metric, value, as_of, age_minutes from menura.metric_snapshot "
                        + "where external_post_id = ? order by offset_minutes, metric")) {
            statement.setObject(1, postId);
            try (ResultSet row = statement.executeQuery()) {
                List<SnapshotValue> snapshots = new ArrayList<>();
                while (row.next()) {
                    snapshots.add(new SnapshotValue(postId, row.getInt(1), row.getString(2), nullableLong(row, 3),
                            row.getTimestamp(4).toInstant(), row.getInt(5)));
                }
                return snapshots;
            }
        }
    }

    @Override
    public List<PointValue> points(Connection connection, UUID postId, String metric, int limit) throws SQLException {
        String sql = "select metric, value, collected_at from menura.metric_point where external_post_id = ?"
                + (metric == null ? "" : " and metric = ?") + " order by collected_at, id limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, postId);
            if (metric != null) {
                statement.setString(index++, metric);
            }
            statement.setInt(index, limit);
            return pointValues(statement);
        }
    }

    /** Marks a day for a rebuild. The row lock is held until the caller's transaction ends (see {@link #lockDirtyDay}). */
    private static void markDirty(Connection connection, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.metric_rollup_dirty (day) values (?) on conflict (day) do update set day = excluded.day")) {
            statement.setObject(1, day);
            statement.executeUpdate();
        }
    }

    private static void insertRollups(Connection connection, String table, String periodColumn, List<Rollup> rows)
            throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into " + table + " (" + periodColumn + ", channel_id, format, publish_hour, series_id, "
                        + "layout_template, brand_kit_version, source, estimate, metric, posts, values_count, total) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Rollup rollup : rows) {
                Grain grain = rollup.grain();
                insert.setObject(1, rollup.period());
                insert.setObject(2, grain.channelId());
                insert.setString(3, grain.format());
                setNullableInt(insert, 4, grain.publishHour());
                insert.setObject(5, grain.seriesId());
                insert.setString(6, grain.layoutTemplate());
                setNullableInt(insert, 7, grain.brandKitVersion());
                insert.setString(8, grain.source());
                insert.setBoolean(9, grain.estimate());
                insert.setString(10, rollup.metric());
                insert.setInt(11, rollup.posts());
                insert.setInt(12, rollup.valuesCount());
                insert.setLong(13, rollup.total());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<Rollup> rollupRows(Connection connection, String table, String periodColumn, LocalDate from,
                                           LocalDate to, Set<String> roles) throws SQLException {
        String sql = "select r." + periodColumn + ", r.channel_id, r.format, r.publish_hour, r.series_id, r.layout_template, "
                + "r.brand_kit_version, r.source, r.estimate, r.metric, r.posts, r.values_count, r.total from " + table
                + " r left join menura.content_series cs on cs.id = r.series_id "
                + "where r." + periodColumn + " >= ? and r." + periodColumn + " <= ?"
                + (roles == null ? "" : " and (cs.id is null or cs.visible_to && ?)");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, from);
            statement.setObject(2, to);
            if (roles != null) {
                statement.setArray(3, connection.createArrayOf("text", roles.stream().sorted().toArray()));
            }
            try (ResultSet row = statement.executeQuery()) {
                List<Rollup> rows = new ArrayList<>();
                while (row.next()) {
                    Grain grain = new Grain(row.getObject(2, UUID.class), row.getString(3), nullableInt(row, 4),
                            row.getObject(5, UUID.class), row.getString(6), nullableInt(row, 7), row.getString(8),
                            row.getBoolean(9));
                    rows.add(new Rollup(row.getObject(1, LocalDate.class), grain, row.getString(10), row.getInt(11),
                            row.getInt(12), row.getLong(13)));
                }
                return rows;
            }
        }
    }

    private List<CollectionStatus> statusRows(Connection connection, String where, UUID channelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select channel_id, outcome, last_success_at, last_failure_at, failure_reason "
                        + "from menura.metric_collection_status " + where)) {
            if (channelId != null) {
                statement.setObject(1, channelId);
            }
            try (ResultSet row = statement.executeQuery()) {
                List<CollectionStatus> statuses = new ArrayList<>();
                while (row.next()) {
                    statuses.add(new CollectionStatus(row.getObject(1, UUID.class), row.getString(2),
                            instant(row.getTimestamp(3)), instant(row.getTimestamp(4)), row.getString(5)));
                }
                return statuses;
            }
        }
    }

    private static List<PointValue> pointValues(PreparedStatement statement) throws SQLException {
        try (ResultSet row = statement.executeQuery()) {
            List<PointValue> values = new ArrayList<>();
            while (row.next()) {
                values.add(new PointValue(row.getString(1), row.getLong(2), row.getTimestamp(3).toInstant()));
            }
            return values;
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static Integer nullableInt(ResultSet row, int column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet row, int column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
