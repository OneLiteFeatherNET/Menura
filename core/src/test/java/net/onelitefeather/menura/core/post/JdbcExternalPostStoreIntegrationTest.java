package net.onelitefeather.menura.core.post;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.DeterministicRandom;
import net.onelitefeather.menura.core.channel.support.RecordingAudit;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.support.ListingPlatform;
import net.onelitefeather.menura.core.secret.SecretGenerator;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * External posts against a real PostgreSQL 17: the unique key holds under concurrent re-sync runs, and a re-sync of an
 * unchanged channel writes no post row (checked through the row transaction ids).
 */
@Tag("integration")
@Testcontainers
class JdbcExternalPostStoreIntegrationTest {

    private static final Instant T0 = ChannelFixture.NOW;
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void theUniqueKeyHoldsWhenTwoRunsSyncTheSameChannelAtOnce() throws Exception {
        World world = new World();
        UUID channel = world.connectedChannel("UC-concurrent");
        world.platform.list(List.of(ListingPlatform.item("v1", "One", T0), ListingPlatform.item("v2", "Two", T0),
                ListingPlatform.item("v3", "Three", T0)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = pool.submit(() -> {
                start.await();
                return world.service.syncChannel(channel);
            });
            Future<?> second = pool.submit(() -> {
                start.await();
                return world.service.syncChannel(channel);
            });
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(3, rowCount("select count(*) from menura.external_post where channel_id = '" + channel + "'"),
                "three items make three records, however the two runs interleave");
    }

    @Test
    void aSecondRunOverAnUnchangedChannelRewritesNoPostRow() throws Exception {
        World world = new World();
        UUID channel = world.connectedChannel("UC-unchanged");
        world.platform.list(List.of(ListingPlatform.item("v1", "One", T0), ListingPlatform.item("v2", "Two", T0)));
        world.service.syncChannel(channel);
        Map<String, String> before = rowVersions(channel);

        world.service.syncChannel(channel);

        assertEquals(before, rowVersions(channel), "an unchanged re-sync writes no post row");
        assertEquals(2, before.size(), "both records are still there");
    }

    @Test
    void theDatabaseRefusesASecondRecordWithTheSameChannelAndPlatformIdentifier() throws Exception {
        World world = new World();
        UUID channel = world.connectedChannel("UC-duplicate");
        world.platform.list(List.of(ListingPlatform.item("v1", "One", T0)));
        world.service.syncChannel(channel);
        ExternalPost existing = world.service.overview(channel, null, null, 10).posts().get(0);
        ExternalPost copy = new ExternalPost(UUID.randomUUID(), channel, existing.platform(), existing.platformItemId(),
                "https://video.example.test/copy", "Copy", "video", T0, null, T0, T0);

        assertThrows(SQLException.class, () -> world.transactions.inTransaction(c -> {
            world.posts.insert(c, copy);
            return null;
        }), "a second record for the same channel and platform identifier is refused by the table");
    }

    @Test
    void aRemovedPostKeepsItsRowAndItsRemovalTime() throws Exception {
        World world = new World();
        UUID channel = world.connectedChannel("UC-removed");
        world.platform.list(List.of(ListingPlatform.item("v1", "One", T0)));
        world.service.syncChannel(channel);
        world.platform.list(List.of());

        world.service.syncChannel(channel);

        assertEquals(1, rowCount("select count(*) from menura.external_post where channel_id = '" + channel
                + "' and removed_at is not null"), "the removed post is kept with its removal time");
    }

    /** One fresh database schema per test class, and one world of services per test. */
    private final class World {

        final ChannelFixture fixtures = new ChannelFixture();
        final ListingPlatform platform = new ListingPlatform();
        final PlatformRegistry registry = new PlatformRegistry(List.of(platform));
        final DataSourceTransactions transactions = new DataSourceTransactions(dataSource());
        final JdbcChannelStore channels = new JdbcChannelStore(TokenCipher.of(KEY, null, new SecureRandom()));
        final JdbcExternalPostStore posts = new JdbcExternalPostStore();
        final ExternalPostService service;
        final ConnectionService connections;

        World() throws SQLException {
            migrated();
            connections = new ConnectionService(transactions, fixtures.clock,
                    new IdentifierGenerator(new DeterministicRandom(3L)), new SecretGenerator(new DeterministicRandom(4L)),
                    channels, new RecordingAudit(), registry, Duration.ofMinutes(10), 0.8);
            service = new ExternalPostService(transactions, fixtures.clock,
                    new IdentifierGenerator(new DeterministicRandom(5L)), channels, posts, connections, registry);
        }

        /** Stores a connected channel of the listing platform with a grant that does not expire during the test. */
        UUID connectedChannel(String account) throws SQLException {
            UUID id = UUID.nameUUIDFromBytes(("listing-" + account).getBytes(StandardCharsets.UTF_8));
            transactions.inTransaction(c -> {
                channels.insertChannel(c, new Channel(id, ListingPlatform.PLATFORM, account, "Channel " + account,
                        ConnectionState.CONNECTED, null, Set.of(), Set.of("listing.account"), null, T0, T0, null));
                channels.saveGrant(c, new ChannelGrant(id, "token-" + account, null, T0.plus(Duration.ofHours(1)), T0));
                return null;
            });
            return id;
        }
    }

    private void migrated() {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    /** The transaction id of every post row of a channel, keyed by its record id: a rewritten row gets a new one. */
    private Map<String, String> rowVersions(UUID channel) throws SQLException {
        Map<String, String> versions = new java.util.TreeMap<>();
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select id, xmin::text as version from menura.external_post "
                     + "where channel_id = '" + channel + "' order by id")) {
            while (rows.next()) {
                versions.put(rows.getString("id"), rows.getString("version"));
            }
        }
        return versions;
    }

    private long rowCount(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), "the count query returns a row");
            return rows.getLong(1);
        }
    }
}
