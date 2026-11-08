package net.onelitefeather.menura.server.prototype.analytics;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Optional;

/**
 * The metrics Menura can show and the platforms that deliver them through their APIs. A metric a platform does not
 * deliver is never a zero: it is missing for that platform.
 */
public final class MetricCatalogue {

    public static final String YOUTUBE = "youtube";
    public static final String TIKTOK = "tiktok";
    public static final String INSTAGRAM = "instagram";

    private static final List<String> ALL_PLATFORMS = List.of(YOUTUBE, TIKTOK, INSTAGRAM);

    /** One catalogue entry as served by GET /api/prototype/analytics/metrics. */
    @Serdeable
    public record Metric(String id, String label, List<String> platforms) {
    }

    public static final List<Metric> METRICS = List.of(
            new Metric("views", "Aufrufe", ALL_PLATFORMS),
            new Metric("engagedViews", "Engaged Views", List.of(YOUTUBE)),
            new Metric("avgViewPercent", "Ø angesehen (%)", List.of(YOUTUBE)),
            new Metric("avgWatchSeconds", "Ø Wiedergabezeit (s)", List.of(YOUTUBE, INSTAGRAM)),
            new Metric("likes", "Likes", ALL_PLATFORMS),
            new Metric("comments", "Kommentare", ALL_PLATFORMS),
            new Metric("shares", "Geteilt", ALL_PLATFORMS),
            new Metric("saves", "Gespeichert", List.of(INSTAGRAM)),
            new Metric("reach", "Reichweite", List.of(INSTAGRAM)),
            new Metric("subscribersGained", "Neue Abonnenten", List.of(YOUTUBE)));

    private MetricCatalogue() {
    }

    static Optional<Metric> find(String id) {
        return METRICS.stream().filter(metric -> metric.id().equals(id)).findFirst();
    }

    static boolean provides(Metric metric, String platform) {
        return metric.platforms().contains(platform);
    }

    /** Averages are combined as means over posts; every other metric is a count and is summed. */
    static boolean isAverage(Metric metric) {
        return "avgViewPercent".equals(metric.id()) || "avgWatchSeconds".equals(metric.id());
    }
}
