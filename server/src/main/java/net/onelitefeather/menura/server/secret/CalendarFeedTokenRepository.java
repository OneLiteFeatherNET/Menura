package net.onelitefeather.menura.server.secret;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores calendar feed tokens. Every call runs on the connection of the transaction it belongs to. */
public interface CalendarFeedTokenRepository {

    /** The token with this identifier, if it is not revoked. */
    Optional<FeedToken> findActive(Connection connection, UUID id) throws SQLException;

    /** Revokes every active token of the person. */
    void revokeActive(Connection connection, UUID personId, Instant revokedAt) throws SQLException;

    void insert(Connection connection, FeedToken token) throws SQLException;
}
