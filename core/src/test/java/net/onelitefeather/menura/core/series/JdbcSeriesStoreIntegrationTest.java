package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.post.ExternalPostFormat;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.DeterministicRandom;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.TokenCipher;
import net.onelitefeather.menura.core.series.SeriesReports.Episode;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.core.series.support.FixedBrandTokens;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * Content series against a real PostgreSQL 17: a series round-trips with its badge texts and playlists, concurrent
 * assignments to one series never share an episode number, and two concurrent decisions on one post leave one of them
 * refused.
 */
@Tag("integration")
@Testcontainers
class JdbcSeriesStoreIntegrationTest {

    private static final Instant T0 = ChannelFixture.NOW;
    private static final UUID EDITOR = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");
    /** A low-entropy placeholder key for the channel token cipher of this test only; it protects no real token. */
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void aSeriesRoundTripsWithItsBadgeTextAndItsPlaylists() throws Exception {
        World world = new World();
        UUID channel = world.channel("UC-round");
        ContentSeries stored = world.transactions.inTransaction(c -> world.service.create(c,
                SeriesFixture.draftWithPlaylist("Kubernetes", "#kube", channel, "PL-1")));

        ContentSeries read = world.transactions.inTransaction(c -> world.series.find(c, stored.id()).orElseThrow());

        assertEquals(stored.badgeText(), read.badgeText(), "the badge texts round-trip per language");
        assertEquals("PL-1", read.playlists().get(channel).playlistId(), "the playlist of the channel round-trips");
        assertEquals("#kube", read.hashtag(), "the hashtag round-trips");
    }

    @Test
    void aSeriesRoundTripsItsVisibleRolesAndAHiddenSeriesAnswersNotFound() throws Exception {
        World world = new World();
        SeriesDraft base = SeriesFixture.draft("Redaktion", "#redaktion");
        SeriesDraft limited = new SeriesDraft(base.name(), base.badgeText(), base.colourToken(), base.badgeToken(),
                base.openingFormula(), base.layoutTemplate(), base.hashtag(), base.audience(), base.weekday(),
                base.postingSlot(), base.active(), base.playlists(), List.of("editor", "operator"));
        UUID id = world.transactions.inTransaction(c -> world.service.create(c, limited).id());

        ContentSeries read = world.transactions.inTransaction(c -> world.series.find(c, id).orElseThrow());

        assertEquals(Set.of(Role.EDITOR, Role.OPERATOR), read.visibleTo(), "the visible roles round-trip");
        ProblemException hidden = assertThrows(ProblemException.class, () -> world.transactions.inTransaction(
                c -> world.service.find(c, id, Set.of(Role.VIEWER))));
        assertEquals(404, hidden.status(), "a viewer does not see the series and gets 404");
    }

    @Test
    void concurrentAssignmentsToOneSeriesNeverShareAnEpisodeNumber() throws Exception {
        World world = new World();
        UUID channel = world.channel("UC-numbers");
        UUID series = world.transactions.inTransaction(c -> world.service.create(c,
                SeriesFixture.draft("Serie", "#numbers")).id());
        List<UUID> posts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            posts.add(world.insertPost(channel, "n" + i));
        }

        List<Future<?>> results = runConcurrently(posts.size(), i -> world.transactions.inTransaction(c ->
                world.service.assign(c, posts.get(i), new AssignmentCommand(series, false, false), EDITOR)));
        for (Future<?> result : results) {
            result.get();
        }

        List<Episode> episodes = world.transactions.inTransaction(c -> world.service.episodes(c, series));
        assertEquals(8, episodes.size(), "every post received a number");
        assertEquals(8, episodes.stream().map(Episode::number).distinct().count(),
                "no two posts of the series share an episode number");
    }

    @Test
    void twoConcurrentDecisionsOnOnePostLeaveOneAndRefuseTheOther() throws Exception {
        World world = new World();
        UUID channel = world.channel("UC-race");
        UUID first = world.transactions.inTransaction(c -> world.service.create(c, SeriesFixture.draft("Erste", "#erste")).id());
        UUID second = world.transactions.inTransaction(c -> world.service.create(c, SeriesFixture.draft("Zweite", "#zweite")).id());
        UUID post = world.insertPost(channel, "race");

        List<Future<?>> results = runConcurrently(2, i -> world.transactions.inTransaction(c -> world.service.assign(c,
                post, new AssignmentCommand(i == 0 ? first : second, false, false), EDITOR)));
        int accepted = 0;
        int refused = 0;
        for (Future<?> result : results) {
            try {
                result.get();
                accepted++;
            } catch (java.util.concurrent.ExecutionException failure) {
                assertTrue(failure.getCause() instanceof ProblemException p && p.status() == 409,
                        "the refused decision is a 409 conflict: " + failure.getCause());
                refused++;
            }
        }

        assertEquals(1, accepted, "exactly one decision on the post is accepted");
        assertEquals(1, refused, "the other decision is refused with a conflict");
    }

    /** Starts the tasks at the same moment and waits until each has ended; the caller reads each outcome from its future. */
    private static List<Future<?>> runConcurrently(int count, Task task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.run(index);
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException outcome) {
                    // Read by the caller through the same future.
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return futures;
    }

    @FunctionalInterface
    private interface Task {
        Object run(int index) throws SQLException;
    }

    /** One migrated database and the services over it, per test. */
    private final class World {

        final ChannelFixture fixtures = new ChannelFixture();
        final DataSourceTransactions transactions = new DataSourceTransactions(dataSource());
        final JdbcChannelStore channels = new JdbcChannelStore(TokenCipher.of(KEY, null, new SecureRandom()));
        final JdbcExternalPostStore posts = new JdbcExternalPostStore();
        final JdbcSeriesStore series = new JdbcSeriesStore();
        final SeriesService service;

        World() {
            Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            service = new SeriesService(fixtures.clock, new IdentifierGenerator(new DeterministicRandom(21L)), series,
                    posts, channels, new FixedBrandTokens()
                    .colour("on-primary", "#ffffff").colour("primary", "#6638e4").template(SeriesFixture.TEMPLATE),
                    JoinDomain.NONE);
        }

        UUID channel(String account) throws SQLException {
            UUID id = UUID.nameUUIDFromBytes(("series-" + account).getBytes(StandardCharsets.UTF_8));
            transactions.inTransaction(c -> {
                channels.insertChannel(c, new Channel(id, "listing", account, "Channel " + account,
                        ConnectionState.CONNECTED, null, Set.of(), Set.of(), null, T0, T0, null));
                return null;
            });
            return id;
        }

        UUID insertPost(UUID channel, String item) throws SQLException {
            ExternalPost post = SeriesFixture.post(channel, item, ExternalPostFormat.SHORT, T0);
            transactions.inTransaction(c -> {
                posts.insert(c, post);
                return null;
            });
            return post.id();
        }
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }
}
