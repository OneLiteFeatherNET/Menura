package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The join tables against a real PostgreSQL 17: concurrent deliveries of one event id and of one answer store one row,
 * the rollup rebuild restores the counts of the stored joins, and the post read sees the series of a post.
 */
@Tag("integration")
@Testcontainers
class JdbcJoinStoreIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 8);
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String PSEUDONYM = "d".repeat(64);

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void twoConcurrentDeliveriesOfOneEventIdStoreOneEventAndCountItOnce() throws Exception {
        JoinService service = service(NOW);
        UUID event = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

        List<Boolean> created = race(2, () -> service.recordJoin(
                new JoinPayloads.EventInput(event, true, NOW, PSEUDONYM)).created());

        assertEquals(1, created.stream().filter(Boolean::booleanValue).count(), "exactly one delivery creates the event");
        assertEquals(1L, counted("total"), "the join is counted once");
    }

    @Test
    void twoConcurrentAnswersForOneJoinStoreOneAnswer() throws Exception {
        JoinService service = service(NOW);
        UUID event = UUID.fromString("1b2c3d4e-5f60-4b7c-9d8e-0f1a2b3c4d5e");
        service.recordJoin(new JoinPayloads.EventInput(event, true, NOW, PSEUDONYM));

        List<Boolean> created = race(2, () -> service.recordAnswer(event, StatedSource.TIKTOK).created());

        assertEquals(1, created.stream().filter(Boolean::booleanValue).count(), "exactly one answer is stored");
        assertEquals(1L, counted("tiktok"), "the answer is counted once");
    }

    @Test
    void aRebuiltDayEqualsTheAcceptedFirstJoinsAfterACorrection() throws Exception {
        JoinService service = service(NOW);
        service.recordJoin(new JoinPayloads.EventInput(UUID.fromString("2c3d4e5f-6071-4c8d-8e9f-1a2b3c4d5e6f"), true,
                NOW, PSEUDONYM));
        service.recordJoin(new JoinPayloads.EventInput(UUID.fromString("3d4e5f60-7182-4d9e-9f01-2b3c4d5e6f70"), true,
                NOW.plusSeconds(5), PSEUDONYM));
        execute("update menura.join_count_day set joins = 9 where day = date '2026-11-08' and source = 'total'");

        service.rebuildDay(DAY);

        assertEquals(2L, counted("total"), "the rebuilt total equals the stored first joins");
    }

    @Test
    void aPostReadsTheSeriesItBelongsToAndTheRolesThatMaySeeIt() throws Exception {
        migrate();
        UUID channel = UUID.fromString("4e5f6071-8293-4ea0-8f12-3c4d5e6f7081");
        UUID post = UUID.fromString("5f607182-93a4-4fb1-9023-4d5e6f708192");
        UUID series = UUID.fromString("6071829a-a4b5-4c2d-8134-5e6f708193a4");
        execute("insert into menura.channel (id, platform, platform_account_id, display_name, state, created_at, updated_at) "
                + "values ('" + channel + "', 'youtube', 'UC-test', 'Test', 'connected', now(), now())");
        execute("insert into menura.external_post (id, channel_id, platform, platform_item_id, url, title, format, "
                + "published_at, created_at, updated_at) values ('" + post + "', '" + channel + "', 'youtube', 'v1', "
                + "'https://example.invalid/v1', 'Short', 'short', timestamptz '2026-11-08 10:00:00+00', now(), now())");
        execute("insert into menura.content_series (id, name, colour_token, badge_token, opening_formula, layout_template, "
                + "hashtag, audience, weekday, posting_slot, active, visible_to, created_at, updated_at) values ('" + series
                + "', 'Serie', 'primary', 'on-primary', 'Kurz erklärt:', 'short-9x16', '#serie', 'players', 1, 12, true, "
                + "ARRAY['admin']::text[], now(), now())");
        execute("insert into menura.content_series_assignment (post_id, series_id, no_series, assigned_at) values ('" + post
                + "', '" + series + "', false, now())");

        JoinStore store = new JdbcJoinStore();
        try (Connection connection = dataSource().getConnection()) {
            var found = store.findPost(connection, post).orElseThrow();

            assertEquals("youtube", found.platform(), "the platform of the post is read");
            assertEquals(Set.of(Role.ADMIN), found.seriesRoles(), "the roles that may see its series are read");
            assertFalse(found.visibleTo(Set.of(Role.VIEWER)), "a viewer may not see a post of an admin-only series");
        }
    }

    private JoinService service(Instant now) throws SQLException {
        migrate();
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        return new JoinService(new DataSourceTransactions(dataSource()), clock, ZONE, new JdbcJoinStore());
    }

    /** Runs the same call from several threads at once, and returns each thread's result in start order. */
    private static <T> List<T> race(int threads, Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private long counted(String source) throws SQLException {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery("select joins from menura.join_count_day where day = date '2026-11-08' "
                     + "and source = '" + source + "'")) {
            return rows.next() ? rows.getLong(1) : 0L;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void migrate() {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }
}
