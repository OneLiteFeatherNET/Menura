package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.Rollups.Grain;
import net.onelitefeather.menura.core.analytics.Rollups.Input;
import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.Rollups.SnapshotInput;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.secret.TokenCipher;
import net.onelitefeather.menura.core.series.JdbcSeriesStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dashboard reads over two years of generated values, answered from the rollups and each measured against the
 * one-second budget. The data has a fixed seed, two channels, four publish hours a day and twenty series, so the run is
 * repeatable and small enough for CI. Every test generates its own data in its own database.
 */
@Tag("integration")
@Testcontainers
class AnalyticsRollupReadIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final long SEED = 20261109L;
    private static final LocalDate FIRST = LocalDate.of(2024, 11, 1);
    private static final LocalDate LAST = LocalDate.of(2026, 10, 31);
    private static final int SERIES_COUNT = 20;
    private static final int[] HOURS = {9, 12, 18, 21};
    private static final List<UUID> CHANNEL_IDS = List.of(
            UUID.nameUUIDFromBytes("channel-read-a".getBytes(StandardCharsets.UTF_8)),
            UUID.nameUUIDFromBytes("channel-read-b".getBytes(StandardCharsets.UTF_8)));
    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void theQuarterAggregateByChannelAndFormatIsAnsweredFromRollupsWithinOneSecond() throws Exception {
        World world = new World();
        Generated generated = world.generate();

        long started = System.nanoTime();
        AnalyticsReader.Overview overview = world.reader.overview(LAST.minusDays(89), LAST, Set.of(Role.VIEWER));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(generated.quarterViews(), views(overview.totals()), 0.0,
                "the quarter views are the sum of the generated daily views");
        assertEquals(CHANNEL_IDS.size(), overview.channels().size(), "one row per channel");
        assertEquals(2, overview.formats().size(), "one row per format");
        assertTrue(elapsedMillis < 1000, "the quarter aggregate took " + elapsedMillis + " ms, the budget is one second");
    }

    @Test
    void theTwoYearAggregateIsAnsweredFromRollupsWithinOneSecond() throws Exception {
        World world = new World();
        Generated generated = world.generate();

        long started = System.nanoTime();
        AnalyticsReader.Overview overview = world.reader.overview(FIRST, LAST, Set.of(Role.VIEWER));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(generated.posts(), overview.totals().posts(), "every generated post is counted once");
        assertEquals(generated.twoYearViews(), views(overview.totals()), 0.0,
                "the two-year views are the sum of the generated daily views");
        assertTrue(elapsedMillis < 1000, "the two-year aggregate took " + elapsedMillis + " ms, the budget is one second");
    }

    @Test
    void theSeriesComparisonOf52WeeksOver20SeriesIsAnsweredFromRollupsWithinOneSecond() throws Exception {
        World world = new World();
        world.generate();

        long started = System.nanoTime();
        AnalyticsReader.SeriesComparison comparison = world.reader.series(LAST.minusDays(363), LAST, Set.of(Role.VIEWER));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(SERIES_COUNT, comparison.series().stream().filter(s -> s.seriesId() != null).count(),
                "every generated series is compared");
        assertTrue(elapsedMillis < 1000, "the series comparison took " + elapsedMillis + " ms, the budget is one second");
    }

    @Test
    void theEqualAgeComparisonOverTwoYearsIsAnsweredFromRollupsWithinOneSecond() throws Exception {
        World world = new World();
        Generated generated = world.generate();

        long started = System.nanoTime();
        AnalyticsReader.EqualAge comparison = world.reader.equalAge(Metrics.VIEWS, Duration.ofHours(24),
                AnalyticsReader.By.HOUR, null);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        long used = comparison.slots().stream().mapToLong(Aggregation.Slot::posts).sum();
        assertEquals(generated.posts(), used, "every generated post has a +24 hour snapshot and is used once");
        assertTrue(elapsedMillis < 1000, "the equal-age comparison took " + elapsedMillis + " ms, the budget is one second");
    }

    private static double views(Aggregation.Group group) {
        return group.metrics().stream().filter(t -> t.name().equals(Metrics.VIEWS)).findFirst().orElseThrow().value();
    }

    /** What the generator wrote: the expected totals of the reads. */
    private record Generated(double quarterViews, double twoYearViews, long posts) {
    }

    /** One migrated database, the stores and the readers over it, for one test. */
    private final class World {

        final DataSourceTransactions transactions;
        final JdbcMetricStore metrics;
        final AnalyticsReader reader;
        final Random random = new Random(SEED);

        World() throws SQLException {
            Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            transactions = new DataSourceTransactions(dataSource());
            metrics = new JdbcMetricStore(new IdentifierGenerator(new SecureRandom()), new PublishSlot(ZONE));
            JdbcChannelStore channels = new JdbcChannelStore(TokenCipher.of(KEY, null, new SecureRandom()));
            transactions.inTransaction(c -> {
                for (int i = 0; i < CHANNEL_IDS.size(); i++) {
                    channels.insertChannel(c, new Channel(CHANNEL_IDS.get(i), "metrics", "UC-read-" + i,
                            "Channel read " + i, ConnectionState.CONNECTED, null, Set.of(), Set.of("metrics.account"), null,
                            NOW, NOW, null));
                }
                return null;
            });
            reader = new AnalyticsReader(transactions, Clock.fixed(NOW, ZoneOffset.UTC), new PublishSlot(ZONE), metrics,
                    new JdbcExternalPostStore(), channels, new JdbcSeriesStore());
        }

        /**
         * Writes two years of daily, weekly and equal-age rows: one post per channel, publish hour and day, each of a series
         * chosen by the day and hour, with every platform metric. The writes go through the same primitives as the job.
         */
        Generated generate() throws SQLException {
            TreeMap<LocalDate, List<Rollup>> dailyRows = new TreeMap<>();
            double quarterViews = 0;
            double twoYearViews = 0;
            long posts = 0;
            for (LocalDate day = FIRST; !day.isAfter(LAST); day = day.plusDays(1)) {
                List<Input> inputs = new ArrayList<>();
                List<SnapshotInput> snapshots = new ArrayList<>();
                for (int channelIndex = 0; channelIndex < CHANNEL_IDS.size(); channelIndex++) {
                    UUID channel = CHANNEL_IDS.get(channelIndex);
                    for (int hourIndex = 0; hourIndex < HOURS.length; hourIndex++) {
                        int hour = HOURS[hourIndex];
                        UUID post = UUID.nameUUIDFromBytes((day + "-" + channel + "-" + hour).getBytes(StandardCharsets.UTF_8));
                        UUID series = seriesOf(day, channelIndex, hourIndex);
                        String format = hourIndex % 2 == 0 ? "short" : "long";
                        Grain grain = Grain.platform(channel, format, hour, series, null, null);
                        for (String metric : Metrics.ALL) {
                            long value = metric.equals(Metrics.AVERAGE_VIEW_PERCENTAGE) ? 1 + random.nextInt(100)
                                    : 1000 + random.nextInt(9000);
                            inputs.add(new Input(post, grain, metric, value));
                            if (metric.equals(Metrics.VIEWS)) {
                                twoYearViews += value;
                                if (!day.isBefore(LAST.minusDays(89))) {
                                    quarterViews += value;
                                }
                                snapshots.add(new SnapshotInput(post, channel, hour, day.getDayOfWeek().getValue(), 1440,
                                        metric, value));
                            }
                        }
                        posts++;
                    }
                }
                List<Rollup> rows = Rollups.daily(day, inputs);
                List<OffsetGroup> groups = Rollups.offsets(day, snapshots);
                LocalDate currentDay = day;
                transactions.inTransaction(c -> {
                    metrics.replaceDay(c, currentDay, rows);
                    metrics.replaceOffsets(c, currentDay, groups);
                    return null;
                });
                dailyRows.put(day, rows);
            }
            // The weekly rows, summed from the daily rows of each week.
            for (LocalDate week = MetricRollups.weekStart(FIRST); !week.isAfter(LAST); week = week.plusWeeks(1)) {
                List<Rollup> days = new ArrayList<>();
                for (LocalDate day = week; day.isBefore(week.plusDays(7)); day = day.plusDays(1)) {
                    days.addAll(dailyRows.getOrDefault(day, List.of()));
                }
                List<Rollup> weekly = Rollups.weekly(week, days);
                LocalDate weekStart = week;
                transactions.inTransaction(c -> {
                    metrics.replaceWeek(c, weekStart, weekly);
                    return null;
                });
            }
            return new Generated(quarterViews, twoYearViews, posts);
        }

        private UUID seriesOf(LocalDate day, int channelIndex, int hourIndex) {
            long dayIndex = ChronoUnit.DAYS.between(FIRST, day);
            int index = (int) ((dayIndex * HOURS.length + hourIndex + channelIndex) % SERIES_COUNT);
            return UUID.nameUUIDFromBytes(("series-read-" + index).getBytes(StandardCharsets.UTF_8));
        }

        private PGSimpleDataSource dataSource() {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setUrl(postgres.getJdbcUrl());
            dataSource.setUser(postgres.getUsername());
            dataSource.setPassword(postgres.getPassword());
            return dataSource;
        }
    }
}
