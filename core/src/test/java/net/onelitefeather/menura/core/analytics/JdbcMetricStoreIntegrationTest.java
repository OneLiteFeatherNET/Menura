package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.secret.TokenCipher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The metric tables against a real PostgreSQL 17: a point lands in the partition of its month, the latest value replaces
 * the previous one, a snapshot keeps its first value, and an absent metric is stored as null.
 */
@Tag("integration")
@Testcontainers
class JdbcMetricStoreIntegrationTest {

    private static final Instant T0 = ChannelFixture.NOW;
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void aPointIsStoredInThePartitionOfItsMonth() throws Exception {
        World world = new World();
        ExternalPost post = world.post("v1");
        world.inTransaction(c -> {
            world.metrics.ensurePartitions(c, T0);
            return null;
        });

        world.inTransaction(c -> {
            world.metrics.storeReading(c, new Reading(post, Map.of(Metrics.VIEWS, 120L), null, T0));
            return null;
        });

        assertEquals(1, count("select count(*) from menura.metric_point_2026_11"),
                "the point of a November collection lands in the November partition");
    }

    @Test
    void theLatestValueIsReplacedByTheNewerReading() throws Exception {
        World world = new World();
        ExternalPost post = world.post("v2");
        world.inTransaction(c -> {
            world.metrics.ensurePartitions(c, T0);
            return null;
        });
        world.inTransaction(c -> {
            world.metrics.storeReading(c, new Reading(post, Map.of(Metrics.VIEWS, 100L), null, T0));
            return null;
        });

        world.inTransaction(c -> {
            world.metrics.storeReading(c, new Reading(post, Map.of(Metrics.VIEWS, 150L), null, T0.plusSeconds(60)));
            return null;
        });

        assertEquals(1, count("select count(*) from menura.metric_latest where external_post_id = '" + post.id() + "'"),
                "one latest row per post and metric");
        assertEquals(150, count("select value from menura.metric_latest where external_post_id = '" + post.id() + "'"),
                "the latest row holds the newer value");
        assertEquals(2, count("select count(*) from menura.metric_point where external_post_id = '" + post.id() + "'"),
                "the raw points keep both collected values");
    }

    @Test
    void aSnapshotKeepsItsFirstValue() throws Exception {
        World world = new World();
        ExternalPost post = world.post("v3");
        world.inTransaction(c -> {
            world.metrics.ensurePartitions(c, T0);
            return null;
        });
        SnapshotValue first = new SnapshotValue(post.id(), 60, Metrics.VIEWS, 90L, T0, 60);
        SnapshotValue second = new SnapshotValue(post.id(), 60, Metrics.VIEWS, 999L, T0.plusSeconds(3600), 120);
        world.inTransaction(c -> {
            world.metrics.storeSnapshots(c, List.of(first));
            world.metrics.storeSnapshots(c, List.of(second));
            return null;
        });

        assertEquals(90, count("select value from menura.metric_snapshot where external_post_id = '" + post.id() + "'"),
                "a snapshot that exists is not replaced by a later one");
    }

    @Test
    void anAbsentMetricIsStoredAsNullInTheSnapshot() throws Exception {
        World world = new World();
        ExternalPost post = world.post("v4");
        world.inTransaction(c -> {
            world.metrics.ensurePartitions(c, T0);
            return null;
        });
        world.inTransaction(c -> {
            world.metrics.storeSnapshots(c, List.of(new SnapshotValue(post.id(), 60, Metrics.WATCH_TIME, null, T0, 60)));
            return null;
        });

        List<SnapshotValue> stored = world.inTransaction(c -> world.metrics.snapshotsOf(c, post.id()));

        assertNull(stored.get(0).value(), "the watch time of the snapshot is absent, not zero");
        assertTrue(!stored.get(0).asOf().isBefore(T0), "the snapshot keeps its as-of time");
        assertEquals(LocalDate.parse("2026-11-02"), LocalDate.ofInstant(stored.get(0).asOf(), ZoneId.of("Europe/Berlin")),
                "the as-of time is the collection day in the team's zone");
    }

    @Test
    void theAggregateReadsReturnTheLatestValuesAndTheSnapshotsOfThePeriod() throws Exception {
        World world = new World();
        ExternalPost post = world.post("v5");
        world.inTransaction(c -> {
            world.metrics.ensurePartitions(c, T0);
            world.metrics.storeReading(c, new Reading(post, Map.of(Metrics.VIEWS, 70L), null, T0));
            world.metrics.storeSnapshots(c, List.of(new SnapshotValue(post.id(), 60, Metrics.VIEWS, 40L, T0, 60)));
            return null;
        });

        List<Observation> latest = world.inTransaction(c -> world.metrics.latest(c,
                T0.minus(Duration.ofDays(1)), T0.plus(Duration.ofDays(1)), Set.of("viewer")));
        List<Observation> snapshots = world.inTransaction(c -> world.metrics.snapshots(c, Duration.ofHours(1),
                Metrics.VIEWS, channelOf(world)));

        assertEquals(70L, latest.get(0).value(), "the period read returns the latest value of the post");
        assertEquals(40L, snapshots.get(0).value(), "the snapshot read returns the snapshot of the offset");
    }

    private static UUID channelOf(World world) {
        return world.channel;
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        }
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    /** One migrated database and the stores over it, for one test. */
    private final class World {

        final DataSourceTransactions transactions;
        final JdbcChannelStore channels;
        final JdbcExternalPostStore posts = new JdbcExternalPostStore();
        final JdbcMetricStore metrics = new JdbcMetricStore(new IdentifierGenerator(new SecureRandom()),
            new PublishSlot(ZoneId.of("Europe/Berlin")));
        final UUID channel = UUID.nameUUIDFromBytes("channel-metrics".getBytes(StandardCharsets.UTF_8));

        World() throws SQLException {
            Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            transactions = new DataSourceTransactions(dataSource());
            channels = new JdbcChannelStore(TokenCipher.of(KEY, null, new SecureRandom()));
            transactions.inTransaction(c -> {
                channels.insertChannel(c, new Channel(channel, "metrics", "UC-metrics", "Channel metrics",
                        ConnectionState.CONNECTED, null, Set.of(), Set.of("metrics.account"), null, T0, T0, null));
                return null;
            });
        }

        ExternalPost post(String item) throws SQLException {
            ExternalPost post = ExternalPost.create(UUID.nameUUIDFromBytes(("post-" + item).getBytes(StandardCharsets.UTF_8)),
                    channel, "metrics", new net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem(item,
                    "https://video.example.test/" + item, "Title " + item, T0.minus(Duration.ofHours(5)), "short"), T0);
            transactions.inTransaction(c -> {
                posts.insert(c, post);
                return null;
            });
            return post;
        }

        <T> T inTransaction(net.onelitefeather.menura.core.db.Work<T> work) throws SQLException {
            return transactions.inTransaction(work);
        }
    }
}
