package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.auth.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL implementation of {@link JoinStore} over the tables of migration V14 and the external posts of V7 and V9. */
public final class JdbcJoinStore implements JoinStore {

    private static final String POST_SELECT = "select p.id, p.platform, p.published_at, s.id, s.name, s.visible_to "
            + "from menura.external_post p "
            + "left join menura.content_series_assignment a on a.post_id = p.id "
            + "left join menura.content_series s on s.id = a.series_id ";

    @Override
    public Optional<JoinSettings> findSettings(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select join_domain, attribution_window_hours, updated_at, updated_by from menura.join_settings where id = 1")) {
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JoinSettings(row.getString("join_domain"),
                        row.getInt("attribution_window_hours"), instant(row, "updated_at"),
                        row.getObject("updated_by", UUID.class)));
            }
        }
    }

    @Override
    public void saveSettings(Connection connection, JoinSettings settings) throws SQLException {
        String sql = "insert into menura.join_settings (id, join_domain, attribution_window_hours, updated_at, updated_by) "
                + "values (1, ?, ?, ?, ?) on conflict (id) do update set join_domain = excluded.join_domain, "
                + "attribution_window_hours = excluded.attribution_window_hours, updated_at = excluded.updated_at, "
                + "updated_by = excluded.updated_by";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, settings.joinDomain());
            statement.setInt(2, settings.attributionWindowHours());
            statement.setTimestamp(3, Timestamp.from(settings.updatedAt()));
            statement.setObject(4, settings.updatedBy());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<JoinEvent> findEvent(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select event_id, first_join, joined_at, pseudonym, received_at from menura.join_event where event_id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JoinEvent(row.getObject("event_id", UUID.class), row.getBoolean("first_join"),
                        instant(row, "joined_at"), row.getString("pseudonym"), instant(row, "received_at")));
            }
        }
    }

    @Override
    public boolean insertEvent(Connection connection, JoinEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.join_event (event_id, first_join, joined_at, pseudonym, received_at) "
                        + "values (?, ?, ?, ?, ?) on conflict (event_id) do nothing")) {
            statement.setObject(1, event.eventId());
            statement.setBoolean(2, event.firstJoin());
            statement.setTimestamp(3, Timestamp.from(event.joinedAt()));
            statement.setString(4, event.pseudonym());
            statement.setTimestamp(5, Timestamp.from(event.receivedAt()));
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<JoinAnswer> findAnswer(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select event_id, source, answered_at from menura.join_source_answer where event_id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JoinAnswer(row.getObject("event_id", UUID.class),
                        StatedSource.parse(row.getString("source")).orElseThrow(), instant(row, "answered_at")));
            }
        }
    }

    @Override
    public boolean insertAnswer(Connection connection, JoinAnswer answer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.join_source_answer (event_id, source, answered_at) values (?, ?, ?) "
                        + "on conflict (event_id) do nothing")) {
            statement.setObject(1, answer.eventId());
            statement.setString(2, answer.source().wire());
            statement.setTimestamp(3, Timestamp.from(answer.answeredAt()));
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public List<FirstJoin> firstJoinsBetween(Connection connection, Instant from, Instant to) throws SQLException {
        String sql = "select j.event_id, j.joined_at, a.source from menura.join_event j "
                + "left join menura.join_source_answer a on a.event_id = j.event_id "
                + "where j.first_join and j.joined_at between ? and ? order by j.joined_at, j.event_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(from));
            statement.setTimestamp(2, Timestamp.from(to));
            try (ResultSet rows = statement.executeQuery()) {
                List<FirstJoin> joins = new ArrayList<>();
                while (rows.next()) {
                    joins.add(new FirstJoin(rows.getObject("event_id", UUID.class), instant(rows, "joined_at"),
                            StatedSource.parse(rows.getString("source")).orElse(null)));
                }
                return joins;
            }
        }
    }

    @Override
    public List<AttributablePost> postsPublishedBetween(Connection connection, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                POST_SELECT + "where p.published_at between ? and ? order by p.published_at, p.id")) {
            statement.setTimestamp(1, Timestamp.from(from));
            statement.setTimestamp(2, Timestamp.from(to));
            return posts(statement);
        }
    }

    @Override
    public Optional<AttributablePost> findPost(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(POST_SELECT + "where p.id = ?")) {
            statement.setObject(1, postId);
            List<AttributablePost> found = posts(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    @Override
    public void addToDay(Connection connection, LocalDate day, String source, long delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.join_count_day (day, source, joins) values (?, ?, ?) "
                        + "on conflict (day, source) do update set joins = menura.join_count_day.joins + excluded.joins")) {
            statement.setObject(1, day);
            statement.setString(2, source);
            statement.setLong(3, delta);
            statement.executeUpdate();
        }
    }

    @Override
    public void replaceDay(Connection connection, LocalDate day, Map<String, Long> counts) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("delete from menura.join_count_day where day = ?")) {
            delete.setObject(1, day);
            delete.executeUpdate();
        }
        for (Map.Entry<String, Long> counter : counts.entrySet()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into menura.join_count_day (day, source, joins) values (?, ?, ?)")) {
                insert.setObject(1, day);
                insert.setString(2, counter.getKey());
                insert.setLong(3, counter.getValue());
                insert.executeUpdate();
            }
        }
    }

    @Override
    public List<DayCount> countsBetween(Connection connection, LocalDate from, LocalDate to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select day, source, joins from menura.join_count_day where day between ? and ? order by day, source")) {
            statement.setObject(1, from);
            statement.setObject(2, to);
            try (ResultSet rows = statement.executeQuery()) {
                List<DayCount> counts = new ArrayList<>();
                while (rows.next()) {
                    counts.add(new DayCount(rows.getObject("day", LocalDate.class), rows.getString("source"),
                            rows.getLong("joins")));
                }
                return counts;
            }
        }
    }

    private static List<AttributablePost> posts(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            List<AttributablePost> posts = new ArrayList<>();
            while (rows.next()) {
                posts.add(new AttributablePost(rows.getObject(1, UUID.class), rows.getString(2),
                        rows.getTimestamp(3).toInstant(),
                        rows.getObject(4, UUID.class), rows.getString(5), roles(rows.getArray(6))));
            }
            return posts;
        }
    }

    private static Set<Role> roles(java.sql.Array array) throws SQLException {
        Set<Role> roles = new HashSet<>();
        if (array == null) {
            return roles;
        }
        for (Object name : (Object[]) array.getArray()) {
            Role.parse(String.valueOf(name)).ifPresent(roles::add);
        }
        return roles;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getTimestamp(column).toInstant();
    }
}
