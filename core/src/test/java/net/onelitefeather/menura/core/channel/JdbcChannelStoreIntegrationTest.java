package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.db.Work;
import net.onelitefeather.menura.core.secret.TokenCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Channels, grants and flows against a real PostgreSQL 17: encryption at rest, one active channel per account and
 * one-time consent states.
 */
@Tag("integration")
@Testcontainers
class JdbcChannelStoreIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII));

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void grantTokensAreEncryptedAtRestAndReadBackDecrypted() throws SQLException {
        Fixture fixture = migrated();
        UUID channel = fixture.connectedChannel("UC1");

        fixture.saveGrant(new ChannelGrant(channel, "ya29.PLAIN-ACCESS", "1//PLAIN-REFRESH", T0, T0));

        byte[] stored = scalarBytes("select access_token from menura.channel_grant where channel_id = '" + channel + "'");
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("PLAIN"),
                "the database must not hold the access token in readable form");
        ChannelGrant read = fixture.findGrant(channel);
        assertEquals("ya29.PLAIN-ACCESS", read.accessToken(), "the application reads the token back decrypted");
        assertEquals("1//PLAIN-REFRESH", read.refreshToken());
    }

    @Test
    void aGrantCopiedToAnotherChannelDoesNotDecrypt() throws SQLException {
        Fixture fixture = migrated();
        UUID first = fixture.connectedChannel("UC2");
        UUID second = fixture.connectedChannel("UC3");
        fixture.saveGrant(new ChannelGrant(first, "ya29.x", null, T0, T0));
        byte[] moved = scalarBytes("select access_token from menura.channel_grant where channel_id = '" + first + "'");
        execute("insert into menura.channel_grant (channel_id, access_token, updated_at) values ('" + second
                + "', decode('" + hex(moved) + "', 'hex'), now())");

        assertThrows(IllegalStateException.class, () -> fixture.findGrant(second),
                "a ciphertext bound to one channel must not decrypt for another");
    }

    @Test
    void grantsWrittenUnderThePreviousKeyStillReadAfterRotation() throws SQLException {
        Fixture old = migrated();
        UUID channel = old.connectedChannel("UC4");
        old.saveGrant(new ChannelGrant(channel, "ya29.before", null, T0, T0));
        JdbcChannelStore rotated = new JdbcChannelStore(TokenCipher.of(OTHER_KEY, KEY, new SecureRandom()));

        ChannelGrant read = old.transactions.inTransaction(c -> rotated.findGrant(c, channel)).orElseThrow();

        assertEquals("ya29.before", read.accessToken(), "a grant written under the previous key is still readable");
    }

    @Test
    void grantThatNoConfiguredKeyOpensIsAnErrorNotAnEmptyToken() throws SQLException {
        Fixture fixture = migrated();
        UUID channel = fixture.connectedChannel("UC5");
        fixture.saveGrant(new ChannelGrant(channel, "ya29.k", null, T0, T0));
        JdbcChannelStore stranger = new JdbcChannelStore(TokenCipher.of(OTHER_KEY, null, new SecureRandom()));

        assertThrows(IllegalStateException.class,
                () -> fixture.transactions.inTransaction(c -> stranger.findGrant(c, channel)),
                "a grant the configured keys cannot open is an error");
    }

    @Test
    void disconnectDeletesTheGrantRow() throws SQLException {
        Fixture fixture = migrated();
        UUID channel = fixture.connectedChannel("UC6");
        fixture.saveGrant(new ChannelGrant(channel, "ya29.d", "1//d", T0, T0));

        fixture.transactions.inTransaction(c -> {
            fixture.store.deleteGrant(c, channel);
            return null;
        });

        assertEquals(0, scalar("select count(*) from menura.channel_grant where channel_id = '" + channel + "'"),
                "the stored tokens are gone after a disconnect");
    }

    @Test
    void theDatabaseRefusesASecondActiveChannelForOnePlatformAccount() throws SQLException {
        Fixture fixture = migrated();
        fixture.connectedChannel("UC7");

        assertThrows(SQLException.class, () -> fixture.connectedChannel("UC7"),
                "one platform account is one active channel");
    }

    @Test
    void aDisconnectedChannelFreesTheAccountForANewConnection() throws SQLException {
        Fixture fixture = migrated();
        UUID old = fixture.connectedChannel("UC8");
        fixture.transactions.inTransaction(c -> {
            Channel channel = fixture.store.findChannel(c, old).orElseThrow();
            fixture.store.updateChannel(c, channel.disconnected("revoked", T0));
            return null;
        });

        UUID fresh = fixture.connectedChannel("UC8");

        assertFalse(old.equals(fresh), "the new connection is a new channel");
    }

    @Test
    void channelRoundTripsItsStateReasonFeaturesAndScopes() throws SQLException {
        Fixture fixture = migrated();
        UUID id = fixture.connectedChannel("UC9");
        fixture.transactions.inTransaction(c -> {
            Channel channel = fixture.store.findChannel(c, id).orElseThrow();
            fixture.store.updateChannel(c, channel.needsReconnection("refresh token rejected", T0));
            return null;
        });

        Channel read = fixture.transactions.inTransaction(c -> fixture.store.findChannel(c, id)).orElseThrow();

        assertEquals(ConnectionState.NEEDS_RECONNECTION, read.state());
        assertEquals("refresh token rejected", read.stateReason());
        assertEquals(Set.of("publish"), read.features());
        assertEquals(Set.of("https://www.googleapis.com/auth/youtube.upload"), read.grantedScopes());
    }

    @Test
    void consentStateIsStoredAsDigestAndCanBeTakenOnlyOnce() throws SQLException {
        Fixture fixture = migrated();
        byte[] digest = new byte[32];
        digest[0] = 7;
        fixture.transactions.inTransaction(c -> {
            fixture.store.insertFlow(c, new ChannelStore.ConnectionFlow(digest, UUID.randomUUID(), "youtube",
                    Set.of("publish"), null, "verifier-value", T0));
            return null;
        });

        assertTrue(fixture.transactions.inTransaction(c -> fixture.store.takeFlow(c, digest)).isPresent(),
                "the first use succeeds");
        assertEquals(Optional.empty(), fixture.transactions.inTransaction(c -> fixture.store.takeFlow(c, digest)),
                "a consent state is used once");
    }

    @Test
    void auditStatusIsStoredPerPlatformAndReplaced() throws SQLException {
        Fixture fixture = migrated();
        fixture.transactions.inTransaction(c -> {
            fixture.store.saveAuditStatus(c, new ChannelStore.PlatformAuditStatus("youtube", AuditStatus.UNAUDITED, T0, "first"));
            fixture.store.saveAuditStatus(c, new ChannelStore.PlatformAuditStatus("youtube", AuditStatus.AUDITED, T0, "second"));
            return null;
        });

        ChannelStore.PlatformAuditStatus read = fixture.transactions
                .inTransaction(c -> fixture.store.findAuditStatus(c, "youtube")).orElseThrow();

        assertEquals(AuditStatus.AUDITED, read.status());
        assertEquals("second", read.source());
    }

    @Test
    void theDatabaseKeepsNoPlainTokenInAnyColumnOfTheGrantTable() throws SQLException {
        Fixture fixture = migrated();
        UUID channel = fixture.connectedChannel("UC10");
        fixture.saveGrant(new ChannelGrant(channel, "ya29.ROW-SECRET", "1//ROW-SECRET", T0, T0));

        assertEquals(0, scalar("select count(*) from menura.channel_grant where position(convert_to('ROW-SECRET', 'UTF8') in "
                + "access_token) > 0 or position(convert_to('ROW-SECRET', 'UTF8') in refresh_token) > 0"),
                "no column of the grant table holds the token in readable form");
    }

    /** One migrated database, one store with its key and helpers for transactions. */
    private final class Fixture {
        final JdbcChannelStore store = new JdbcChannelStore(TokenCipher.of(KEY, null, new SecureRandom()));
        final DataSourceTransactions transactions = new DataSourceTransactions(dataSource());

        UUID connectedChannel(String account) throws SQLException {
            UUID id = UUID.randomUUID();
            transactions.inTransaction(c -> {
                store.insertChannel(c, new Channel(id, "youtube", account, "Channel " + account, ConnectionState.CONNECTED,
                        null, Set.of("publish"), Set.of("https://www.googleapis.com/auth/youtube.upload"), null, T0, T0, null));
                return null;
            });
            return id;
        }

        void saveGrant(ChannelGrant grant) throws SQLException {
            transactions.inTransaction(c -> {
                store.saveGrant(c, grant);
                return null;
            });
        }

        ChannelGrant findGrant(UUID channel) throws SQLException {
            return transactions.inTransaction(c -> store.findGrant(c, channel)).orElseThrow();
        }
    }

    private Fixture migrated() {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        return new Fixture();
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        return source;
    }

    private byte[] scalarBytes(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "the row must exist: " + sql);
            return rows.getBytes(1);
        }
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}
