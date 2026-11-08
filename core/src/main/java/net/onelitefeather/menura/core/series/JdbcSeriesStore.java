package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.auth.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** PostgreSQL implementation of {@link SeriesStore} over the tables of migration V9. */
public final class JdbcSeriesStore implements SeriesStore {

    private static final String SERIES_COLUMNS = "id, name, colour_token, badge_token, opening_formula, layout_template, "
            + "hashtag, audience, weekday, posting_slot, active, visible_to, created_at, updated_at";
    private static final String ASSIGNMENT_COLUMNS = "post_id, series_id, no_series, flag, playlist_state, "
            + "playlist_checked_at, assigned_at, assigned_by";

    @Override
    public List<ContentSeries> list(Connection connection) throws SQLException {
        return load(connection, "", null);
    }

    @Override
    public Optional<ContentSeries> find(Connection connection, UUID id) throws SQLException {
        List<ContentSeries> found = load(connection, " where id = ?", id);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public void insert(Connection connection, ContentSeries series) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.content_series (" + SERIES_COLUMNS
                + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindSeries(connection, statement, series);
            statement.executeUpdate();
        }
        writeChildren(connection, series);
    }

    @Override
    public void update(Connection connection, ContentSeries series) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("update menura.content_series set name = ?, "
                + "colour_token = ?, badge_token = ?, opening_formula = ?, layout_template = ?, hashtag = ?, audience = ?, "
                + "weekday = ?, posting_slot = ?, active = ?, visible_to = ?, updated_at = ? where id = ?")) {
            statement.setString(1, series.name());
            statement.setString(2, series.colourToken());
            statement.setString(3, series.badgeToken());
            statement.setString(4, series.openingFormula());
            statement.setString(5, series.layoutTemplate());
            statement.setString(6, series.hashtag());
            statement.setString(7, series.audience().value());
            statement.setInt(8, series.weekday());
            statement.setInt(9, series.postingSlot());
            statement.setBoolean(10, series.active());
            statement.setArray(11, visibleArray(connection, series));
            statement.setTimestamp(12, Timestamp.from(series.updatedAt()));
            statement.setObject(13, series.id());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("the series to update does not exist");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("delete from menura.content_series_badge where series_id = ?")) {
            statement.setObject(1, series.id());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("delete from menura.content_series_playlist where series_id = ?")) {
            statement.setObject(1, series.id());
            statement.executeUpdate();
        }
        writeChildren(connection, series);
    }

    @Override
    public Optional<SeriesAssignment> lockAssignment(Connection connection, UUID postId) throws SQLException {
        // The post row is locked first, so two decisions on a post that has none yet also run one after the other.
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from menura.external_post where id = ? for update")) {
            statement.setObject(1, postId);
            statement.executeQuery().close();
        }
        return assignment(connection, "select " + ASSIGNMENT_COLUMNS + " from menura.content_series_assignment "
                + "where post_id = ? for update", postId);
    }

    @Override
    public Optional<SeriesAssignment> findAssignment(Connection connection, UUID postId) throws SQLException {
        return assignment(connection, "select " + ASSIGNMENT_COLUMNS + " from menura.content_series_assignment "
                + "where post_id = ?", postId);
    }

    @Override
    public List<SeriesAssignment> listAssignments(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select " + ASSIGNMENT_COLUMNS
                + " from menura.content_series_assignment");
             ResultSet row = statement.executeQuery()) {
            List<SeriesAssignment> assignments = new ArrayList<>();
            while (row.next()) {
                assignments.add(assignmentOf(row));
            }
            return assignments;
        }
    }

    @Override
    public void saveAssignment(Connection connection, SeriesAssignment assignment) throws SQLException {
        String sql = "insert into menura.content_series_assignment (" + ASSIGNMENT_COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (post_id) do update set series_id = excluded.series_id, no_series = excluded.no_series, "
                + "flag = excluded.flag, playlist_state = excluded.playlist_state, "
                + "playlist_checked_at = excluded.playlist_checked_at, assigned_at = excluded.assigned_at, "
                + "assigned_by = excluded.assigned_by";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, assignment.postId());
            statement.setObject(2, assignment.seriesId());
            statement.setBoolean(3, assignment.noSeries());
            statement.setString(4, assignment.flag() == null ? null : name(assignment.flag()));
            statement.setString(5, assignment.playlistState() == null ? null : name(assignment.playlistState()));
            statement.setTimestamp(6, timestamp(assignment.playlistCheckedAt()));
            statement.setTimestamp(7, Timestamp.from(assignment.assignedAt()));
            statement.setObject(8, assignment.assignedBy());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Integer> episodeOf(Connection connection, UUID seriesId, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select number from menura.content_series_episode where series_id = ? and post_id = ?")) {
            statement.setObject(1, seriesId);
            statement.setObject(2, postId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(row.getInt(1)) : Optional.empty();
            }
        }
    }

    /**
     * The counter row is created on the first number and then locked by the update, so two assignments to one series take
     * their numbers one after the other and never the same one.
     */
    @Override
    public int nextEpisodeNumber(Connection connection, UUID seriesId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.content_series_counter "
                + "(series_id, last_number) values (?, 0) on conflict (series_id) do nothing")) {
            statement.setObject(1, seriesId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("update menura.content_series_counter "
                + "set last_number = last_number + 1 where series_id = ? returning last_number")) {
            statement.setObject(1, seriesId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("the episode counter of the series does not exist");
                }
                return row.getInt(1);
            }
        }
    }

    @Override
    public void saveEpisode(Connection connection, UUID seriesId, UUID postId, int number) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.content_series_episode "
                + "(series_id, post_id, number) values (?, ?, ?)")) {
            statement.setObject(1, seriesId);
            statement.setObject(2, postId);
            statement.setInt(3, number);
            statement.executeUpdate();
        }
    }

    @Override
    public List<Episode> episodes(Connection connection, UUID seriesId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select number, post_id from "
                + "menura.content_series_episode where series_id = ? order by number")) {
            statement.setObject(1, seriesId);
            try (ResultSet row = statement.executeQuery()) {
                List<Episode> episodes = new ArrayList<>();
                while (row.next()) {
                    episodes.add(new Episode(row.getInt("number"), row.getObject("post_id", UUID.class)));
                }
                return episodes;
            }
        }
    }

    private static List<ContentSeries> load(Connection connection, String filter, UUID id) throws SQLException {
        List<ContentSeries> series = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("select " + SERIES_COLUMNS
                + " from menura.content_series" + filter + " order by name, id")) {
            if (id != null) {
                statement.setObject(1, id);
            }
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    series.add(seriesOf(row));
                }
            }
        }
        if (series.isEmpty()) {
            return series;
        }
        Map<UUID, Map<String, String>> badges = new HashMap<>();
        Map<UUID, Map<UUID, SeriesPlaylist>> playlists = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("select series_id, language, badge_text "
                + "from menura.content_series_badge")) {
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    badges.computeIfAbsent(row.getObject("series_id", UUID.class), ignored -> new LinkedHashMap<>())
                            .put(row.getString("language"), row.getString("badge_text"));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("select series_id, channel_id, playlist_id, "
                + "playlist_name from menura.content_series_playlist")) {
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    playlists.computeIfAbsent(row.getObject("series_id", UUID.class), ignored -> new LinkedHashMap<>())
                            .put(row.getObject("channel_id", UUID.class), new SeriesPlaylist(row.getString("playlist_id"),
                                    row.getString("playlist_name")));
                }
            }
        }
        return series.stream().map(s -> new ContentSeries(s.id(), s.name(),
                badges.getOrDefault(s.id(), Map.of()), s.colourToken(), s.badgeToken(), s.openingFormula(),
                s.layoutTemplate(), s.hashtag(), s.audience(), s.weekday(), s.postingSlot(), s.active(),
                playlists.getOrDefault(s.id(), Map.of()), s.visibleTo(), s.createdAt(), s.updatedAt())).toList();
    }

    private static void writeChildren(Connection connection, ContentSeries series) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.content_series_badge "
                + "(series_id, language, badge_text) values (?, ?, ?)")) {
            for (Map.Entry<String, String> badge : series.badgeText().entrySet()) {
                statement.setObject(1, series.id());
                statement.setString(2, badge.getKey());
                statement.setString(3, badge.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.content_series_playlist "
                + "(series_id, channel_id, playlist_id, playlist_name) values (?, ?, ?, ?)")) {
            for (Map.Entry<UUID, SeriesPlaylist> playlist : series.playlists().entrySet()) {
                statement.setObject(1, series.id());
                statement.setObject(2, playlist.getKey());
                statement.setString(3, playlist.getValue().playlistId());
                statement.setString(4, playlist.getValue().playlistName());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** The visible roles as a text array of their lower-case names, sorted so that equal sets are stored alike. */
    private static java.sql.Array visibleArray(Connection connection, ContentSeries series) throws SQLException {
        String[] names = series.visibleTo().stream().map(role -> role.name().toLowerCase(Locale.ROOT)).sorted()
                .toArray(String[]::new);
        return connection.createArrayOf("text", names);
    }

    private static Set<Role> visibleRoles(ResultSet row) throws SQLException {
        java.sql.Array array = row.getArray("visible_to");
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Object name : (Object[]) array.getArray()) {
            roles.add(Role.valueOf(String.valueOf(name).toUpperCase(Locale.ROOT)));
        }
        return roles;
    }

    private static void bindSeries(Connection connection, PreparedStatement statement, ContentSeries series)
            throws SQLException {
        statement.setObject(1, series.id());
        statement.setString(2, series.name());
        statement.setString(3, series.colourToken());
        statement.setString(4, series.badgeToken());
        statement.setString(5, series.openingFormula());
        statement.setString(6, series.layoutTemplate());
        statement.setString(7, series.hashtag());
        statement.setString(8, series.audience().value());
        statement.setInt(9, series.weekday());
        statement.setInt(10, series.postingSlot());
        statement.setBoolean(11, series.active());
        statement.setArray(12, visibleArray(connection, series));
        statement.setTimestamp(13, Timestamp.from(series.createdAt()));
        statement.setTimestamp(14, Timestamp.from(series.updatedAt()));
    }

    private static ContentSeries seriesOf(ResultSet row) throws SQLException {
        return new ContentSeries(row.getObject("id", UUID.class), row.getString("name"), Map.of(),
                row.getString("colour_token"), row.getString("badge_token"), row.getString("opening_formula"),
                row.getString("layout_template"), row.getString("hashtag"),
                Audience.parse(row.getString("audience")).orElseThrow(), row.getInt("weekday"),
                row.getInt("posting_slot"), row.getBoolean("active"), Map.of(), visibleRoles(row),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }

    private static Optional<SeriesAssignment> assignment(Connection connection, String sql, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, postId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(assignmentOf(row)) : Optional.empty();
            }
        }
    }

    private static SeriesAssignment assignmentOf(ResultSet row) throws SQLException {
        String flag = row.getString("flag");
        String playlist = row.getString("playlist_state");
        return new SeriesAssignment(row.getObject("post_id", UUID.class), row.getObject("series_id", UUID.class),
                row.getBoolean("no_series"), flag == null ? null : SeriesAssignment.Flag.valueOf(flag.toUpperCase(Locale.ROOT)),
                playlist == null ? null : SeriesAssignment.PlaylistState.valueOf(playlist.toUpperCase(Locale.ROOT)),
                instant(row.getTimestamp("playlist_checked_at")), row.getTimestamp("assigned_at").toInstant(),
                row.getObject("assigned_by", UUID.class));
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
