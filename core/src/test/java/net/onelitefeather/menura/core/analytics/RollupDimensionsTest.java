package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.analytics.support.AnalyticsFixture;
import net.onelitefeather.menura.core.post.ExternalPost;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The series and render dimensions of a value are captured at write time and carried into the rollups. */
class RollupDimensionsTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
    private static final UUID SERIES_A = UUID.nameUUIDFromBytes("series-a-dims".getBytes());
    private static final UUID SERIES_B = UUID.nameUUIDFromBytes("series-b-dims".getBytes());

    private static Rollup views(AnalyticsFixture world) throws SQLException {
        world.rollUp();
        return world.metrics.dailyRows(null, DAY, DAY, null).stream()
                .filter(row -> row.grain().platformValue() && row.metric().equals(Metrics.VIEWS))
                .findFirst().orElseThrow();
    }

    private static ExternalPost post(AnalyticsFixture world, UUID channel) throws SQLException {
        return world.publishedPost(channel, "dims", NOW.minus(Duration.ofHours(5)), "short");
    }

    @Test
    void aRenderedPostCarriesItsLayoutTemplateAndBrandKitVersionIntoTheRollup() throws Exception {
        AnalyticsFixture world = new AnalyticsFixture(true);
        world.metrics.ensurePartitions(null, NOW);
        UUID channel = world.connectedChannel("dims-rendered");
        ExternalPost post = post(world, channel);
        world.metrics.storeReading(null, new Reading(post, Map.of(Metrics.VIEWS, 10L), null, NOW, "vertical-clean", 3));

        Rollup row = views(world);

        assertEquals("vertical-clean", row.grain().layoutTemplate(), "the layout template is captured at write time");
        assertEquals(3, row.grain().brandKitVersion(), "the brand kit version is captured at write time");
    }

    @Test
    void anImportedPostStoresTheRenderDimensionsAsAbsent() throws Exception {
        AnalyticsFixture world = new AnalyticsFixture(true);
        world.metrics.ensurePartitions(null, NOW);
        UUID channel = world.connectedChannel("dims-imported");
        ExternalPost post = post(world, channel);
        world.metrics.storeReading(null, new Reading(post, Map.of(Metrics.VIEWS, 10L), null, NOW));

        Rollup row = views(world);

        assertNull(row.grain().layoutTemplate(), "an imported post was not rendered by Menura: no layout template");
        assertNull(row.grain().brandKitVersion(), "an imported post was not rendered by Menura: no brand kit version");
    }

    @Test
    void aSeriesReassignmentAppliesToTheReadingsCollectedAfterIt() throws Exception {
        AnalyticsFixture world = new AnalyticsFixture(true);
        world.metrics.ensurePartitions(null, NOW);
        UUID channel = world.connectedChannel("dims-series");
        ExternalPost post = post(world, channel);
        world.metrics.storeReading(null, new Reading(post, Map.of(Metrics.VIEWS, 10L), SERIES_A, NOW));
        world.metrics.storeReading(null, new Reading(post, Map.of(Metrics.VIEWS, 20L), SERIES_B,
                NOW.plus(Duration.ofHours(1))));

        Rollup row = views(world);

        assertEquals(SERIES_B, row.grain().seriesId(), "the rollup of the day follows the series of the latest reading");
        assertEquals(SERIES_A, world.metrics.points().get(0).seriesId(), "the first point keeps the series it was collected with");
        assertEquals(SERIES_B, world.metrics.points().get(1).seriesId(), "the next point carries the new series");
    }

    @Test
    void theDayOfAPublishedPostIsTheDayInTheInstallationTimeZone() {
        PublishSlot slots = new PublishSlot(ZoneId.of("Europe/Berlin"));

        assertEquals(LocalDate.of(2026, 11, 9), slots.day(Instant.parse("2026-11-08T23:30:00Z")),
                "23:30 UTC is already the next day in Berlin");
    }
}
