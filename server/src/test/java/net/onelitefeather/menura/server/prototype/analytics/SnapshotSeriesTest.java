package net.onelitefeather.menura.server.prototype.analytics;

import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.Model;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test derives its own series from a fresh seed store, so the order of the tests does not matter. */
class SnapshotSeriesTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    private static List<Model.Post> seedPosts() {
        return new DemoStore(Clock.fixed(NOW, ZoneOffset.UTC)).posts(null, null);
    }

    private static Model.Post post(String id) {
        return seedPosts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void snapshotsAreCumulativeAndNeverDecrease() {
        for (Model.Post post : seedPosts()) {
            List<Snapshot> series = SnapshotSeed.of(post);
            for (String metric : Set.of("views", "engagedViews", "likes", "comments", "shares", "reach", "saves")) {
                Double previous = null;
                for (Snapshot snapshot : series) {
                    Double value = snapshot.values().get(metric);
                    if (value == null) {
                        continue;
                    }
                    if (previous != null) {
                        assertTrue(value >= previous, post.id() + " " + metric + " fell at " + snapshot.offset());
                    }
                    previous = value;
                }
            }
        }
    }

    @Test
    void theSeriesCoversAllSevenOffsetsInOrder() {
        List<String> labels = SnapshotSeed.of(post("p-41")).stream().map(s -> s.offset().label()).toList();

        assertEquals(List.of("+1h", "+2h", "+4h", "+8h", "+24h", "+48h", "+7d"), labels);
    }

    @Test
    void eachSnapshotLiesAtThePublishTimePlusItsOffset() {
        Model.Post post = post("p-41");
        Instant published = Instant.parse(post.publishedAt());

        for (Snapshot snapshot : SnapshotSeed.of(post)) {
            assertEquals(published.plus(snapshot.offset().duration()), snapshot.at(), snapshot.offset().label());
        }
    }

    @Test
    void tiktokSnapshotsHaveNoEngagedViews() {
        Model.Post tiktok = post("p-42");

        for (Snapshot snapshot : SnapshotSeed.of(tiktok)) {
            assertFalse(snapshot.value("engagedViews").isPresent(), "TikTok does not deliver engaged views");
        }
    }

    @Test
    void theMetricsOfEachPlatformAreExactlyTheCataloguedOnes() {
        for (String platform : List.of(MetricCatalogue.YOUTUBE, MetricCatalogue.TIKTOK, MetricCatalogue.INSTAGRAM)) {
            Model.Post post = seedPosts().stream().filter(p -> p.platform().equals(platform)).findFirst().orElseThrow();
            Set<String> catalogued = MetricCatalogue.METRICS.stream()
                    .filter(metric -> metric.platforms().contains(platform))
                    .map(MetricCatalogue.Metric::id)
                    .collect(Collectors.toSet());
            Snapshot sevenDays = SnapshotSeed.of(post).get(Snapshot.Offset.D7.ordinal());

            assertEquals(catalogued, sevenDays.values().keySet(), platform);
        }
    }

    @Test
    void theSevenDaySnapshotHasTheV1ViewsAndEngagedViews() {
        for (Model.Post post : seedPosts()) {
            Snapshot sevenDays = SnapshotSeed.of(post).get(Snapshot.Offset.D7.ordinal());

            assertEquals((double) post.engagedViews(), sevenDays.values().get("views"), post.id() + " views");
            if (post.platform().equals(MetricCatalogue.YOUTUBE)) {
                assertEquals((double) post.engagedViews(), sevenDays.values().get("engagedViews"),
                        post.id() + " engagedViews");
            }
        }
    }

    @Test
    void theSevenDaySnapshotHasTheV1AverageViewPercentOfYouTube() {
        Model.Post youtube = post("p-41");

        Snapshot sevenDays = SnapshotSeed.of(youtube).get(Snapshot.Offset.D7.ordinal());

        assertEquals(youtube.avgViewPercent(), sevenDays.values().get("avgViewPercent"));
    }

    @Test
    void theDerivationIsDeterministic() {
        assertEquals(SnapshotSeed.of(post("p-43")), SnapshotSeed.of(post("p-43")));
    }

}
