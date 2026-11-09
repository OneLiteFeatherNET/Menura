package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.support.DeterministicRandom;
import net.onelitefeather.menura.core.channel.support.MutableClock;
import net.onelitefeather.menura.core.channel.support.RecordingAudit;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.publish.PostService.DraftCommand;
import net.onelitefeather.menura.core.publish.PostService.PlanCommand;
import net.onelitefeather.menura.core.publish.PostService.PlanItem;
import net.onelitefeather.menura.core.publish.support.ScriptedAdapter;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.TokenCipher;
import net.onelitefeather.menura.core.series.BrandTokens;
import net.onelitefeather.menura.core.series.JdbcSeriesStore;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.core.auth.Role;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The content distribution against a real PostgreSQL 17: approval, the dry run, the publication job with a transient
 * failure and its retry, the external post and its origin, recycling, multi-channel planning, the calendar window and the
 * feed, on a freshly migrated database. The platform is a scripted adapter; no test reaches a platform.
 */
@Tag("integration")
@Testcontainers
class JdbcPostStoreIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant SLOT = Instant.parse("2026-11-09T17:00:00Z");
    private static final UUID EDITOR = UUID.fromString("e0170000-0000-4000-8000-0000000000a1");
    private static final UUID PUBLISHER = UUID.fromString("b0b00000-0000-4000-8000-0000000000a1");
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void theWholeFlowRunsOnAFreshDatabase() throws Exception {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        World world = new World();
        UUID main = world.connectedChannel("main");
        UUID second = world.connectedChannel("second");
        UUID clip = world.finishedVideo();

        UUID post = world.service.create(editor(), new DraftCommand(main, clip, null, null, null, "Launch clip", "Text",
                List.of("#launch"), Visibility.PRIVATE, SLOT)).post().id();
        world.service.declareMadeForKids(editor(), post, false);
        world.service.submit(editor(), post);
        world.service.approve(publisher(), post);
        world.service.schedule(publisher(), post);

        world.platform.nextPublish(ScriptedAdapter.rateLimited());
        world.clock.advance(Duration.between(NOW, SLOT));
        assertEquals(1, world.runner.runOnce().retried(), "the rate-limited first attempt is retried later");
        world.clock.advance(Duration.ofMinutes(5));
        assertEquals(1, world.runner.runOnce().published(), "the retry publishes the post");

        assertEquals(PostStatus.PUBLISHED, world.service.get(post).post().status(), "the post is published");
        assertEquals(1, rowCount("select count(*) from menura.external_post where channel_id = '" + main + "'"),
                "the publication is recorded as one external post");
        assertEquals(1, rowCount("select count(*) from menura.external_post_origin where post_id = '" + post + "'"),
                "the external post names the Menura post it came from");
        assertEquals(2, rowCount("select count(*) from menura.post_attempt where post_id = '" + post + "'"),
                "both attempts are recorded");

        UUID recycled = world.service.create(editor(), new DraftCommand(second, clip, null, null, post, "Again", null, null,
                null, null)).post().id();
        assertEquals(post, world.service.get(recycled).post().sourcePostId(), "the recycled post references its source");

        PostService.PlanResult plan = world.service.planChannels(editor(), new PlanCommand(world.finishedVideo(), null, null,
                null, List.of(new PlanItem(main, SLOT.plus(Duration.ofDays(1)), "Planned A", null, List.of(), null),
                        new PlanItem(second, SLOT.plus(Duration.ofDays(1)), "Planned B", null, List.of(), null))));
        assertEquals(2, plan.posts().size(), "one draft per channel");

        CalendarRange week = CalendarRange.of(CalendarRange.View.WEEK, LocalDate.of(2026, 11, 9), 0, ZoneId.of("Europe/Berlin"));
        assertTrue(world.service.calendar(week, null, null, null).items().size() >= 3,
                "the week holds the published post and the planned ones");
        assertTrue(world.service.feedEvents(NOW, true).stream().anyMatch(event -> event.uid().startsWith("post-")),
                "the feed lists the planned posts");
    }

    @Test
    void theStepsAndRevisionsRoundTripThroughTheTables() throws Exception {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        World world = new World();
        UUID channel = world.connectedChannel("round-trip");
        UUID post = world.service.create(editor(), new DraftCommand(channel, world.finishedVideo(), null, null, null,
                "Round trip", "Text", List.of("#a"), Visibility.PRIVATE, SLOT)).post().id();
        world.service.declareMadeForKids(editor(), post, false);
        world.service.submit(editor(), post);
        world.service.approve(publisher(), post);
        world.service.schedule(publisher(), post);
        PostRevision revision = world.service.propose(editor(), post, new Content(null, null, "Changed", null, null, null, null),
                "Clearer title.");

        world.service.rejectRevision(publisher(), post, revision.id());
        PostService.PostDetail stored = world.service.get(post);

        assertEquals("Round trip", stored.post().title(), "the post reads back from the table");
        assertEquals(List.of("#a"), stored.post().hashtags(), "the hashtags round-trip as an array");
        assertEquals(PostRevision.Status.REJECTED, world.service.revisions(post).get(0).status(),
                "the rejected revision is stored as rejected");
        assertEquals(1, rowCount("select count(*) from menura.post_revision where post_id = '" + post + "' and status = 'rejected'"),
                "one rejected revision row");
    }

    /** The services wired as the server wires them, on the migrated database. */
    private final class World {

        final MutableClock clock = new MutableClock(NOW);
        final IdentifierGenerator identifiers = new IdentifierGenerator(new DeterministicRandom(21L));
        final DataSourceTransactions transactions = new DataSourceTransactions(dataSource());
        final JdbcChannelStore channels = new JdbcChannelStore(TokenCipher.of(KEY, null, new SecureRandom()));
        final JdbcPostStore posts = new JdbcPostStore();
        final ScriptedAdapter platform = new ScriptedAdapter(false);
        final PlatformRegistry registry = new PlatformRegistry(List.of(platform));
        final ConnectionService connections = new ConnectionService(transactions, clock, identifiers,
                new SecretGenerator(new DeterministicRandom(22L)), channels, new RecordingAudit(), registry, Duration.ofMinutes(10),
                0.8);
        final SeriesService series = new SeriesService(clock, identifiers, new JdbcSeriesStore(), new JdbcExternalPostStore(),
                channels, NO_BRAND_KIT, JoinDomain.NONE);
        final PostService service = new PostService(transactions, clock, identifiers, posts, channels, series, registry,
                connections, ZoneId.of("Europe/Berlin"));
        final PublicationRunner runner = new PublicationRunner(transactions, clock, identifiers, posts, channels, connections,
                registry, new JdbcExternalPostStore(), series, PublishSettings.from(java.util.Map.of()));

        World() {
        }

        /** A connected channel of the scripted platform, with a grant that lasts for the whole test. */
        UUID connectedChannel(String account) throws SQLException {
            UUID id = UUID.nameUUIDFromBytes(("publish-" + account).getBytes(StandardCharsets.UTF_8));
            transactions.inTransaction(c -> {
                channels.insertChannel(c, new Channel(id, "youtube", account, "Channel " + account, ConnectionState.CONNECTED,
                        null, Set.of(), Set.of(), null, NOW, NOW, null));
                channels.saveGrant(c, new ChannelGrant(id, "token-" + account, null, NOW.plus(Duration.ofDays(365)), NOW));
                return null;
            });
            return id;
        }

        /** A verified finished video in the asset table, the way an upload leaves it. */
        UUID finishedVideo() throws SQLException {
            UUID id = identifiers.next();
            transactions.inTransaction(c -> {
                try (java.sql.PreparedStatement statement = c.prepareStatement("insert into menura.asset (id, uploader_id, "
                        + "kind, format, duration_seconds, width, height, size_bytes, checksum, storage_key, created_at) "
                        + "values (?, ?, 'video', 'mp4', 600, 1920, 1080, 1000000, 'sha256-test', ?, ?)")) {
                    statement.setObject(1, id);
                    statement.setObject(2, EDITOR);
                    statement.setString(3, "raw/" + id + ".mp4");
                    statement.setTimestamp(4, java.sql.Timestamp.from(NOW));
                    statement.executeUpdate();
                }
                return null;
            });
            return id;
        }
    }

    private static final BrandTokens NO_BRAND_KIT = new BrandTokens() {
        @Override
        public java.util.Optional<net.onelitefeather.menura.core.brand.BrandKit.ColourToken> colour(String name) {
            throw new IllegalStateException("the publication does not read the brand kit");
        }

        @Override
        public boolean hasTemplate(String format) {
            throw new IllegalStateException("the publication does not read the brand kit");
        }
    };

    private static PostActor editor() {
        return new PostActor(EDITOR, Set.of(Role.EDITOR), false);
    }

    private static PostActor publisher() {
        return new PostActor(PUBLISHER, Set.of(Role.PUBLISHER), true);
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
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
