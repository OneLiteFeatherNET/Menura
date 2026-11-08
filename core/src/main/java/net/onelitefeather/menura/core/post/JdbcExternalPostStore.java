package net.onelitefeather.menura.core.post;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL implementation of {@link ExternalPostStore} over the tables of migration V7. */
public final class JdbcExternalPostStore implements ExternalPostStore {

    private static final String COLUMNS = "id, channel_id, platform, platform_item_id, url, title, format, published_at, "
            + "removed_at, created_at, updated_at";
    private static final String ORDER = " order by published_at desc, id desc";

    @Override
    public List<ExternalPost> listForChannel(Connection connection, UUID channelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select " + COLUMNS + " from menura.external_post where channel_id = ?" + ORDER)) {
            statement.setObject(1, channelId);
            return posts(statement);
        }
    }

    @Override
    public List<ExternalPost> page(Connection connection, UUID channelId, Instant afterPublishedAt, UUID afterId,
                                   int limit) throws SQLException {
        boolean continuing = afterPublishedAt != null;
        String sql = "select " + COLUMNS + " from menura.external_post where channel_id = ?"
                + (continuing ? " and (published_at, id) < (?, ?)" : "") + ORDER + " limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, channelId);
            if (continuing) {
                statement.setTimestamp(index++, Timestamp.from(afterPublishedAt));
                statement.setObject(index++, afterId);
            }
            statement.setInt(index, limit);
            return posts(statement);
        }
    }

    @Override
    public void insert(Connection connection, ExternalPost post) throws SQLException {
        String sql = "insert into menura.external_post (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, post.id());
            statement.setObject(2, post.channelId());
            statement.setString(3, post.platform());
            statement.setString(4, post.platformItemId());
            statement.setString(5, post.url());
            statement.setString(6, post.title());
            statement.setString(7, post.format());
            statement.setTimestamp(8, Timestamp.from(post.publishedAt()));
            statement.setTimestamp(9, timestamp(post.removedAt()));
            statement.setTimestamp(10, Timestamp.from(post.createdAt()));
            statement.setTimestamp(11, Timestamp.from(post.updatedAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public void update(Connection connection, ExternalPost post) throws SQLException {
        String sql = "update menura.external_post set url = ?, title = ?, format = ?, published_at = ?, removed_at = ?, "
                + "updated_at = ? where id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, post.url());
            statement.setString(2, post.title());
            statement.setString(3, post.format());
            statement.setTimestamp(4, Timestamp.from(post.publishedAt()));
            statement.setTimestamp(5, timestamp(post.removedAt()));
            statement.setTimestamp(6, Timestamp.from(post.updatedAt()));
            statement.setObject(7, post.id());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("the external post to update does not exist");
            }
        }
    }

    @Override
    public Optional<SyncStatus> findSync(Connection connection, UUID channelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select channel_id, last_success_at, last_failure_at, failure_reason from menura.channel_sync "
                        + "where channel_id = ?")) {
            statement.setObject(1, channelId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SyncStatus(row.getObject("channel_id", UUID.class),
                        instant(row.getTimestamp("last_success_at")), instant(row.getTimestamp("last_failure_at")),
                        row.getString("failure_reason")));
            }
        }
    }

    @Override
    public void saveSync(Connection connection, SyncStatus status) throws SQLException {
        String sql = "insert into menura.channel_sync (channel_id, last_success_at, last_failure_at, failure_reason) "
                + "values (?, ?, ?, ?) on conflict (channel_id) do update set last_success_at = excluded.last_success_at, "
                + "last_failure_at = excluded.last_failure_at, failure_reason = excluded.failure_reason";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, status.channelId());
            statement.setTimestamp(2, timestamp(status.lastSuccessAt()));
            statement.setTimestamp(3, timestamp(status.lastFailureAt()));
            statement.setString(4, status.failureReason());
            statement.executeUpdate();
        }
    }

    private static List<ExternalPost> posts(PreparedStatement statement) throws SQLException {
        List<ExternalPost> posts = new ArrayList<>();
        try (ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                posts.add(new ExternalPost(row.getObject("id", UUID.class), row.getObject("channel_id", UUID.class),
                        row.getString("platform"), row.getString("platform_item_id"), row.getString("url"),
                        row.getString("title"), row.getString("format"), row.getTimestamp("published_at").toInstant(),
                        instant(row.getTimestamp("removed_at")), row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("updated_at").toInstant()));
            }
        }
        return posts;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
