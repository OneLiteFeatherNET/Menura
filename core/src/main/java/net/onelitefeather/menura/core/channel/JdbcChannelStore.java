package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.secret.TokenCipher;

import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PostgreSQL implementation of {@link ChannelStore} over the tables of migration V5. Tokens are written encrypted with
 * {@link TokenCipher}; the additional authenticated data names the column and the channel, so a ciphertext copied to
 * another row or column does not decrypt.
 */
public final class JdbcChannelStore implements ChannelStore {

    private static final String CHANNEL_COLUMNS = "id, platform, platform_account_id, display_name, state, state_reason, "
            + "features, granted_scopes, grant_revocation, created_at, updated_at, disconnected_at";

    private final TokenCipher cipher;

    public JdbcChannelStore(TokenCipher cipher) {
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Override
    public Optional<Channel> findChannel(Connection connection, UUID id) throws SQLException {
        return queryOne(connection, "select " + CHANNEL_COLUMNS + " from menura.channel where id = ?", id);
    }

    @Override
    public Optional<Channel> lockChannel(Connection connection, UUID id) throws SQLException {
        return queryOne(connection, "select " + CHANNEL_COLUMNS + " from menura.channel where id = ? for update", id);
    }

    @Override
    public List<Channel> listChannels(Connection connection, UUID afterId, int limit) throws SQLException {
        String sql = "select " + CHANNEL_COLUMNS + " from menura.channel where (cast(? as uuid) is null or id > cast(? as uuid)) "
                + "order by id limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, afterId);
            statement.setObject(2, afterId);
            statement.setInt(3, limit);
            return channels(statement);
        }
    }

    @Override
    public List<Channel> listInState(Connection connection, ConnectionState state) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select " + CHANNEL_COLUMNS + " from menura.channel where state = ? order by id")) {
            statement.setString(1, state.value());
            return channels(statement);
        }
    }

    @Override
    public Optional<Channel> findActiveByAccount(Connection connection, String platform, String platformAccountId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select " + CHANNEL_COLUMNS
                + " from menura.channel where platform = ? and platform_account_id = ? and state <> 'disconnected'")) {
            statement.setString(1, platform);
            statement.setString(2, platformAccountId);
            List<Channel> found = channels(statement);
            return found.stream().findFirst();
        }
    }

    @Override
    public void insertChannel(Connection connection, Channel channel) throws SQLException {
        String sql = "insert into menura.channel (" + CHANNEL_COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindChannel(connection, statement, channel);
            statement.executeUpdate();
        }
    }

    @Override
    public void updateChannel(Connection connection, Channel channel) throws SQLException {
        String sql = "update menura.channel set platform = ?, platform_account_id = ?, display_name = ?, state = ?, "
                + "state_reason = ?, features = ?, granted_scopes = ?, grant_revocation = ?, created_at = ?, "
                + "updated_at = ?, disconnected_at = ? where id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channel.platform());
            statement.setString(2, channel.platformAccountId());
            statement.setString(3, channel.displayName());
            statement.setString(4, channel.state().value());
            statement.setString(5, channel.stateReason());
            statement.setArray(6, strings(connection, channel.features()));
            statement.setArray(7, strings(connection, channel.grantedScopes()));
            statement.setString(8, channel.grantRevocation());
            statement.setTimestamp(9, Timestamp.from(channel.createdAt()));
            statement.setTimestamp(10, Timestamp.from(channel.updatedAt()));
            statement.setTimestamp(11, timestamp(channel.disconnectedAt()));
            statement.setObject(12, channel.id());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("the channel to update does not exist");
            }
        }
    }

    @Override
    public Optional<ChannelGrant> findGrant(Connection connection, UUID channelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select access_token, refresh_token, access_expires_at, updated_at from menura.channel_grant where channel_id = ?")) {
            statement.setObject(1, channelId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                byte[] refresh = row.getBytes("refresh_token");
                return Optional.of(new ChannelGrant(channelId,
                        decrypt(row.getBytes("access_token"), "access", channelId),
                        refresh == null ? null : decrypt(refresh, "refresh", channelId),
                        instant(row.getTimestamp("access_expires_at")),
                        row.getTimestamp("updated_at").toInstant()));
            }
        }
    }

    @Override
    public void saveGrant(Connection connection, ChannelGrant grant) throws SQLException {
        String sql = "insert into menura.channel_grant (channel_id, access_token, refresh_token, access_expires_at, updated_at) "
                + "values (?, ?, ?, ?, ?) on conflict (channel_id) do update set access_token = excluded.access_token, "
                + "refresh_token = excluded.refresh_token, access_expires_at = excluded.access_expires_at, "
                + "updated_at = excluded.updated_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, grant.channelId());
            statement.setBytes(2, encrypt(grant.accessToken(), "access", grant.channelId()));
            statement.setBytes(3, grant.refreshToken() == null ? null : encrypt(grant.refreshToken(), "refresh", grant.channelId()));
            statement.setTimestamp(4, timestamp(grant.accessExpiresAt()));
            statement.setTimestamp(5, Timestamp.from(grant.updatedAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteGrant(Connection connection, UUID channelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from menura.channel_grant where channel_id = ?")) {
            statement.setObject(1, channelId);
            statement.executeUpdate();
        }
    }

    @Override
    public void insertFlow(Connection connection, ConnectionFlow flow) throws SQLException {
        String sql = "insert into menura.channel_connection_flow (state_hash, person_id, platform, features, channel_id, "
                + "code_verifier, expires_at) values (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, flow.stateHash());
            statement.setObject(2, flow.personId());
            statement.setString(3, flow.platform());
            statement.setArray(4, strings(connection, flow.features()));
            statement.setObject(5, flow.channelId());
            statement.setString(6, flow.codeVerifier());
            statement.setTimestamp(7, Timestamp.from(flow.expiresAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<ConnectionFlow> takeFlow(Connection connection, byte[] stateHash) throws SQLException {
        String sql = "delete from menura.channel_connection_flow where state_hash = ? returning person_id, platform, "
                + "features, channel_id, code_verifier, expires_at, state_hash";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, stateHash);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ConnectionFlow(row.getBytes("state_hash"), row.getObject("person_id", UUID.class),
                        row.getString("platform"), stringSet(row.getArray("features")),
                        row.getObject("channel_id", UUID.class), row.getString("code_verifier"),
                        row.getTimestamp("expires_at").toInstant()));
            }
        }
    }

    @Override
    public Optional<QuotaState> findQuota(Connection connection, UUID channelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select quota_used, quota_limit, quota_reset_at, "
                + "measured_at, rate_limited_until, rate_limit_reason from menura.channel_quota where channel_id = ?")) {
            statement.setObject(1, channelId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new QuotaState(channelId, longOrNull(row, "quota_used"), longOrNull(row, "quota_limit"),
                        instant(row.getTimestamp("quota_reset_at")), instant(row.getTimestamp("measured_at")),
                        instant(row.getTimestamp("rate_limited_until")), row.getString("rate_limit_reason")));
            }
        }
    }

    @Override
    public void saveQuota(Connection connection, QuotaState quota) throws SQLException {
        String sql = "insert into menura.channel_quota (channel_id, quota_used, quota_limit, quota_reset_at, measured_at, "
                + "rate_limited_until, rate_limit_reason) values (?, ?, ?, ?, ?, ?, ?) on conflict (channel_id) do update set "
                + "quota_used = excluded.quota_used, quota_limit = excluded.quota_limit, "
                + "quota_reset_at = excluded.quota_reset_at, measured_at = excluded.measured_at, "
                + "rate_limited_until = excluded.rate_limited_until, rate_limit_reason = excluded.rate_limit_reason";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, quota.channelId());
            statement.setObject(2, quota.quotaUsed());
            statement.setObject(3, quota.quotaLimit());
            statement.setTimestamp(4, timestamp(quota.quotaResetAt()));
            statement.setTimestamp(5, timestamp(quota.measuredAt()));
            statement.setTimestamp(6, timestamp(quota.rateLimitedUntil()));
            statement.setString(7, quota.rateLimitReason());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<PlatformAuditStatus> findAuditStatus(Connection connection, String platform) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select status, decided_at, source from menura.platform_audit_status where platform = ?")) {
            statement.setString(1, platform);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlatformAuditStatus(platform, AuditStatus.parse(row.getString("status")),
                        instant(row.getTimestamp("decided_at")), row.getString("source")));
            }
        }
    }

    @Override
    public void saveAuditStatus(Connection connection, PlatformAuditStatus status) throws SQLException {
        String sql = "insert into menura.platform_audit_status (platform, status, decided_at, source) values (?, ?, ?, ?) "
                + "on conflict (platform) do update set status = excluded.status, decided_at = excluded.decided_at, "
                + "source = excluded.source";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.platform());
            statement.setString(2, status.status().value());
            statement.setTimestamp(3, timestamp(status.decidedAt()));
            statement.setString(4, status.source());
            statement.executeUpdate();
        }
    }

    // ------------------------------------------------------------ helpers

    private Optional<Channel> queryOne(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            return channels(statement).stream().findFirst();
        }
    }

    private static List<Channel> channels(PreparedStatement statement) throws SQLException {
        List<Channel> out = new ArrayList<>();
        try (ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                out.add(new Channel(row.getObject("id", UUID.class), row.getString("platform"),
                        row.getString("platform_account_id"), row.getString("display_name"),
                        ConnectionState.parse(row.getString("state")), row.getString("state_reason"),
                        stringSet(row.getArray("features")), stringSet(row.getArray("granted_scopes")),
                        row.getString("grant_revocation"), row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("updated_at").toInstant(), instant(row.getTimestamp("disconnected_at"))));
            }
        }
        return out;
    }

    private void bindChannel(Connection connection, PreparedStatement statement, Channel channel) throws SQLException {
        statement.setObject(1, channel.id());
        statement.setString(2, channel.platform());
        statement.setString(3, channel.platformAccountId());
        statement.setString(4, channel.displayName());
        statement.setString(5, channel.state().value());
        statement.setString(6, channel.stateReason());
        statement.setArray(7, strings(connection, channel.features()));
        statement.setArray(8, strings(connection, channel.grantedScopes()));
        statement.setString(9, channel.grantRevocation());
        statement.setTimestamp(10, Timestamp.from(channel.createdAt()));
        statement.setTimestamp(11, Timestamp.from(channel.updatedAt()));
        statement.setTimestamp(12, timestamp(channel.disconnectedAt()));
    }

    private byte[] encrypt(String token, String column, UUID channelId) {
        return cipher.encrypt(token.getBytes(StandardCharsets.UTF_8), associated(column, channelId));
    }

    private String decrypt(byte[] stored, String column, UUID channelId) {
        return new String(cipher.decrypt(stored, associated(column, channelId)), StandardCharsets.UTF_8);
    }

    private static byte[] associated(String column, UUID channelId) {
        return (column + ":" + channelId).getBytes(StandardCharsets.UTF_8);
    }

    private static Array strings(Connection connection, Set<String> values) throws SQLException {
        return connection.createArrayOf("text", values.stream().sorted().toArray());
    }

    private static Set<String> stringSet(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList((String[]) array.getArray()));
    }

    private static Long longOrNull(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
