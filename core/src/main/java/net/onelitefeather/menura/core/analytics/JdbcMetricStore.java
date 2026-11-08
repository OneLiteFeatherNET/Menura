package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.id.IdentifierGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

/** PostgreSQL implementation of {@link MetricStore} over the tables of migration V10. */
public final class JdbcMetricStore implements MetricStore {

    private static final String OBSERVATION_COLUMNS = "m.channel_id, m.platform, m.format, m.series_id, m.published_at";

    private final IdentifierGenerator identifiers;

    public JdbcMetricStore(IdentifierGenerator identifiers) {
        this.identifiers = identifiers;
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
        int hour = PublishSlot.hour(reading.post().publishedAt());
        int weekday = PublishSlot.weekday(reading.post().publishedAt());
        try (PreparedStatement delete = connection.prepareStatement(
                "delete from menura.metric_latest where external_post_id = ?")) {
            delete.setObject(1, postId);
            delete.executeUpdate();
        }
        try (PreparedStatement points = connection.prepareStatement(
                "insert into menura.metric_point (id, external_post_id, channel_id, platform, format, publish_hour, "
                        + "publish_weekday, series_id, metric, value, collected_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement latest = connection.prepareStatement(
                     "insert into menura.metric_latest (external_post_id, metric, channel_id, platform, format, "
                             + "published_at, publish_hour, publish_weekday, series_id, value, collected_at) "
                             + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
                latest.setLong(index++, entry.getValue());
                latest.setTimestamp(index, Timestamp.from(reading.collectedAt()));
                latest.addBatch();
            }
            points.executeBatch();
            latest.executeBatch();
        }
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
    public List<Observation> latest(Connection connection, Instant from, Instant to, Set<String> roles)
            throws SQLException {
        String sql = "select m.external_post_id, m.metric, m.value, " + OBSERVATION_COLUMNS + " from menura.metric_latest m "
                + "left join menura.content_series cs on cs.id = m.series_id "
                + "where m.published_at >= ? and m.published_at < ? and (cs.id is null or cs.visible_to && ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(from));
            statement.setTimestamp(2, Timestamp.from(to));
            statement.setArray(3, connection.createArrayOf("text", roles.stream().sorted().toArray()));
            try (ResultSet row = statement.executeQuery()) {
                List<Observation> observations = new ArrayList<>();
                while (row.next()) {
                    observations.add(observation(row));
                }
                return observations;
            }
        }
    }

    @Override
    public List<Observation> snapshots(Connection connection, Duration offset, String metric, UUID channelId)
            throws SQLException {
        String sql = "select s.external_post_id, s.metric, s.value, p.channel_id, p.platform, p.format, p.published_at, "
                + "null::uuid as series_id from menura.metric_snapshot s "
                + "join menura.external_post p on p.id = s.external_post_id "
                + "where s.offset_minutes = ? and s.metric = ?" + (channelId == null ? "" : " and p.channel_id = ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, (int) offset.toMinutes());
            statement.setString(2, metric);
            if (channelId != null) {
                statement.setObject(3, channelId);
            }
            try (ResultSet row = statement.executeQuery()) {
                List<Observation> observations = new ArrayList<>();
                while (row.next()) {
                    observations.add(new Observation(row.getObject(1, UUID.class), row.getObject(4, UUID.class),
                            row.getString(5), row.getString(6), null, row.getTimestamp(7).toInstant(),
                            row.getString(2), nullableLong(row, 3)));
                }
                return observations;
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

    private static Observation observation(ResultSet row) throws SQLException {
        return new Observation(row.getObject(1, UUID.class), row.getObject(4, UUID.class), row.getString(5),
                row.getString(6), row.getObject(7, UUID.class), row.getTimestamp(8).toInstant(), row.getString(2),
                row.getLong(3));
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
