package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.InternalMetrics.Refused;
import net.onelitefeather.menura.core.analytics.InternalMetrics.Source;
import net.onelitefeather.menura.core.analytics.MetricStore.InternalPoint;
import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.support.AnalyticsFixture;
import net.onelitefeather.menura.core.analytics.support.InMemoryMetricStore;
import net.onelitefeather.menura.core.post.ExternalPost;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registered internal sources: refused points store nothing, accepted ones keep their source and estimate flag. */
class InternalMetricsTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final UUID CHANNEL = UUID.nameUUIDFromBytes("channel-internal".getBytes());
    private static final UUID SERIES = UUID.nameUUIDFromBytes("series-internal".getBytes());

    /** A fresh store, registry and clock per test. */
    private static final class World {

        final AnalyticsFixture fixture = new AnalyticsFixture(true);
        final InMemoryMetricStore store = fixture.metrics;
        final InternalMetrics sources = new InternalMetrics(store, Clock.fixed(NOW, ZoneId.of("UTC")),
                List.of(new Source("joins", Set.of("first_joins"))));

        World() throws SQLException {
            store.ensurePartitions(null, NOW);
        }

        void write(InternalPoint point) throws SQLException {
            sources.write(null, point);
        }

        Rollup daily(String source, String metric) {
            return store.dailyRows(null, DAY, DAY, null).stream()
                    .filter(row -> row.grain().source().equals(source) && row.metric().equals(metric))
                    .findFirst().orElseThrow();
        }
    }

    private static InternalPoint joins(UUID channel, UUID post, UUID series, long value, boolean estimate) {
        return new InternalPoint("joins", "first_joins", channel, post, series, DAY, value, estimate);
    }

    @Test
    void aRegisteredSourceWritesItsPointWithSourceAndEstimateFlag() throws SQLException {
        World world = new World();

        world.write(joins(CHANNEL, null, null, 7L, true));
        world.fixture.rollUp();

        assertTrue(world.daily("joins", "first_joins").grain().estimate(), "the estimate flag is kept in the rollup");
        assertEquals(7L, world.daily("joins", "first_joins").total(), "the value is rolled up with its source");
    }

    @Test
    void anUnregisteredSourceIsRefusedAndStoresNothing() throws SQLException {
        World world = new World();
        InternalPoint unknown = new InternalPoint("unknown", "first_joins", CHANNEL, null, null, DAY, 1L, false);

        assertThrows(Refused.class, () -> world.write(unknown), "a source that is not registered is refused");

        assertTrue(world.store.internalPoints().isEmpty(), "nothing is stored for a refused source");
    }

    @Test
    void aMetricTheSourceDidNotRegisterIsRefusedAndStoresNothing() throws SQLException {
        World world = new World();
        InternalPoint other = new InternalPoint("joins", "likes", CHANNEL, null, null, DAY, 1L, false);

        assertThrows(Refused.class, () -> world.write(other), "a metric the source did not register is refused");

        assertTrue(world.store.internalPoints().isEmpty(), "nothing is stored for a refused metric");
    }

    @Test
    void aSourceMayNotRegisterAPlatformMetricName() {
        assertThrows(IllegalArgumentException.class, () -> new Source("joins", Set.of(Metrics.VIEWS)),
                "a source cannot write a platform metric name, so it cannot change a platform point");
    }

    @Test
    void anInternalWriteLeavesThePlatformPointOfThePostUnchanged() throws Exception {
        World world = new World();
        ExternalPost post = world.fixture.publishedPost(CHANNEL, "p1", NOW.minus(Duration.ofHours(5)), "short");
        world.store.storeReading(null, new Reading(post, Map.of(Metrics.VIEWS, 100L), null, NOW));
        world.write(new InternalPoint("joins", "first_joins", CHANNEL, post.id(), null, DAY, 4L, true));

        world.fixture.rollUp();

        assertEquals(100L, world.daily(Rollups.PLATFORM, Metrics.VIEWS).total(),
                "the platform views keep the value the platform reported");
        assertEquals(4L, world.daily("joins", "first_joins").total(), "the internal value is its own row");
    }

    @Test
    void aSeriesScopedPointNamesNoPost() throws SQLException {
        World world = new World();
        world.write(new InternalPoint("joins", "first_joins", null, null, SERIES, DAY, 2L, false));

        assertEquals(1, world.store.internalPoints().size(), "a series point is stored");
        assertFalse(world.store.internalPoints().get(0).point().estimate(), "a measured value is not an estimate");
    }

    @Test
    void aValueMayNotBeNegative() throws SQLException {
        World world = new World();
        InternalPoint negative = new InternalPoint("joins", "first_joins", CHANNEL, null, null, DAY, -1L, false);

        assertThrows(Refused.class, () -> world.write(negative), "a count is never negative");
    }

    @Test
    void aPostScopeNeedsItsChannel() throws SQLException {
        World world = new World();
        InternalPoint noChannel = new InternalPoint("joins", "first_joins", null, UUID.randomUUID(), null, DAY, 1L, false);

        assertThrows(Refused.class, () -> world.write(noChannel), "a post value names the channel of the post");
    }
}
