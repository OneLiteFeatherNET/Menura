package net.onelitefeather.menura.core.post;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of external posts and the re-sync status of channels. Every method runs on the connection of the
 * transaction the caller opened. Inserting a second record with the same channel and platform identifier fails.
 */
public interface ExternalPostStore {

    /** Every post of a channel, for the comparison with a listing. */
    List<ExternalPost> listForChannel(Connection connection, UUID channelId) throws SQLException;

    /** One post by its identifier, if it exists. */
    Optional<ExternalPost> find(Connection connection, UUID id) throws SQLException;

    /**
     * One page of a channel's posts, newest first. The page after {@code afterPublishedAt} and {@code afterId} is
     * returned when both are given; the first page when both are null.
     */
    List<ExternalPost> page(Connection connection, UUID channelId, Instant afterPublishedAt, UUID afterId, int limit)
            throws SQLException;

    void insert(Connection connection, ExternalPost post) throws SQLException;

    void update(Connection connection, ExternalPost post) throws SQLException;

    Optional<SyncStatus> findSync(Connection connection, UUID channelId) throws SQLException;

    void saveSync(Connection connection, SyncStatus status) throws SQLException;

    /** When a channel was last re-synced completely and when it last failed, with the fixed reason of that failure. */
    record SyncStatus(UUID channelId, Instant lastSuccessAt, Instant lastFailureAt, String failureReason) {
    }
}
