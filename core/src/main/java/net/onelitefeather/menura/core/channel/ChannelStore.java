package net.onelitefeather.menura.core.channel;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence of channels, their grants and the connection flows in progress. Every method runs on the connection of
 * the transaction the caller opened. The grant is returned decrypted and written encrypted by the implementation.
 */
public interface ChannelStore {

    Optional<Channel> findChannel(Connection connection, UUID id) throws SQLException;

    /** Reads a channel and locks its row until the transaction ends. Refresh and disconnect use it. */
    Optional<Channel> lockChannel(Connection connection, UUID id) throws SQLException;

    /** Channels in id order after the given id, at most {@code limit}. */
    List<Channel> listChannels(Connection connection, UUID afterId, int limit) throws SQLException;

    List<Channel> listInState(Connection connection, ConnectionState state) throws SQLException;

    /** The channel of this platform account that is not disconnected, if one exists. */
    Optional<Channel> findActiveByAccount(Connection connection, String platform, String platformAccountId)
            throws SQLException;

    void insertChannel(Connection connection, Channel channel) throws SQLException;

    void updateChannel(Connection connection, Channel channel) throws SQLException;

    Optional<ChannelGrant> findGrant(Connection connection, UUID channelId) throws SQLException;

    /** Inserts or replaces the grant of a channel. */
    void saveGrant(Connection connection, ChannelGrant grant) throws SQLException;

    /** Deletes the stored tokens of a channel. */
    void deleteGrant(Connection connection, UUID channelId) throws SQLException;

    void insertFlow(Connection connection, ConnectionFlow flow) throws SQLException;

    /** Removes and returns the flow of a state digest, so a state is used once. Empty when there is none. */
    Optional<ConnectionFlow> takeFlow(Connection connection, byte[] stateHash) throws SQLException;

    Optional<QuotaState> findQuota(Connection connection, UUID channelId) throws SQLException;

    void saveQuota(Connection connection, QuotaState quota) throws SQLException;

    Optional<PlatformAuditStatus> findAuditStatus(Connection connection, String platform) throws SQLException;

    void saveAuditStatus(Connection connection, PlatformAuditStatus status) throws SQLException;

    /** A consent in progress. The state is stored only as its digest; the PKCE verifier lives until the flow ends. */
    record ConnectionFlow(byte[] stateHash, UUID personId, String platform, Set<String> features, UUID channelId,
                          String codeVerifier, Instant expiresAt) {
    }

    /** Measured or reported quota and the rate-limit delay of one channel. Null fields were never reported. */
    record QuotaState(UUID channelId, Long quotaUsed, Long quotaLimit, Instant quotaResetAt, Instant measuredAt,
                      Instant rateLimitedUntil, String rateLimitReason) {
    }

    /** The recorded audit status of a platform app. */
    record PlatformAuditStatus(String platform, AuditStatus status, Instant decidedAt, String source) {
    }
}
