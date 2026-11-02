package net.onelitefeather.menura.server.secret;

import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class JdbcCalendarFeedTokenRepository implements CalendarFeedTokenRepository {

    private static final String FIND_ACTIVE = "select person_id, secret_hash, created_at, revoked_at "
            + "from menura.calendar_feed_token where id = ? and revoked_at is null";
    private static final String REVOKE_ACTIVE = "update menura.calendar_feed_token set revoked_at = ? "
            + "where person_id = ? and revoked_at is null";
    private static final String INSERT = "insert into menura.calendar_feed_token "
            + "(id, person_id, secret_hash, created_at, revoked_at) values (?, ?, ?, ?, ?)";

    @Override
    public Optional<FeedToken> findActive(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE)) {
            statement.setObject(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FeedToken(id, row.getObject("person_id", UUID.class), row.getBytes("secret_hash"),
                        row.getTimestamp("created_at").toInstant(), null));
            }
        }
    }

    @Override
    public void revokeActive(Connection connection, UUID personId, Instant revokedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(REVOKE_ACTIVE)) {
            statement.setTimestamp(1, Timestamp.from(revokedAt));
            statement.setObject(2, personId);
            statement.executeUpdate();
        }
    }

    @Override
    public void insert(Connection connection, FeedToken token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, token.id());
            statement.setObject(2, token.personId());
            statement.setBytes(3, token.secretHash());
            statement.setTimestamp(4, Timestamp.from(token.createdAt()));
            statement.setTimestamp(5, token.revokedAt() == null ? null : Timestamp.from(token.revokedAt()));
            statement.executeUpdate();
        }
    }
}
