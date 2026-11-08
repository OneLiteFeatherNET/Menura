package net.onelitefeather.menura.server.prototype.analytics;

import net.onelitefeather.menura.server.prototype.Model;
import net.onelitefeather.menura.server.prototype.analytics.Snapshot.Offset;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the measurement series of a published post. Everything is a pure function of the post: the id picks the
 * fixed pseudo-random factors, so the same seed always yields the same numbers. The series ends at the v1 views and
 * engaged views at +7 d; earlier offsets are cumulative fractions of those final values and never decrease.
 */
final class SnapshotSeed {

    /** Share of the final value reached at each offset before the jitter of a post. */
    private static final double[] CURVE = {0.08, 0.14, 0.24, 0.36, 0.62, 0.82, 1.0};
    private static final double MAX_BEFORE_FINAL = 0.99;

    private SnapshotSeed() {
    }

    /** All seven snapshots of the post, including offsets that lie in the future of the clock. */
    static List<Snapshot> of(Model.Post post) {
        Instant published = Instant.parse(post.publishedAt());
        long seed = seedOf(post.id());
        Map<String, Double> finals = finalValues(post, seed);
        double[] fractions = fractions(seed);
        List<Snapshot> series = new ArrayList<>();
        for (Offset offset : Offset.values()) {
            double fraction = fractions[offset.ordinal()];
            series.add(new Snapshot(post.id(), post.platform(), offset, published.plus(offset.duration()),
                    valuesAt(finals, fraction)));
        }
        return series;
    }

    /** The values at +7 d, one per metric the platform delivers. */
    private static Map<String, Double> finalValues(Model.Post post, long seed) {
        double views = post.engagedViews();
        Map<String, Double> finals = new LinkedHashMap<>();
        // YouTube counts every start as a view and only some as engaged views, so its views lie 25-45 % above them.
        boolean youTube = MetricCatalogue.YOUTUBE.equals(post.platform());
        finals.put("views", youTube ? (double) Math.round(views * (1.25 + 0.2 * unit(seed, 31))) : views);
        switch (post.platform()) {
            case MetricCatalogue.YOUTUBE -> {
                finals.put("engagedViews", views);
                if (post.avgViewPercent() != null) {
                    finals.put("avgViewPercent", post.avgViewPercent());
                }
                finals.put("avgWatchSeconds", watchSeconds(seed));
                finals.put("likes", share(views, 0.05, 0.03, seed, 3));
                finals.put("comments", share(views, 0.004, 0.006, seed, 4));
                finals.put("shares", share(views, 0.008, 0.012, seed, 5));
                finals.put("subscribersGained", share(views, 0.01, 0.02, seed, 7));
            }
            case MetricCatalogue.TIKTOK -> {
                finals.put("likes", share(views, 0.05, 0.03, seed, 3));
                finals.put("comments", share(views, 0.004, 0.006, seed, 4));
                finals.put("shares", share(views, 0.008, 0.012, seed, 5));
            }
            case MetricCatalogue.INSTAGRAM -> {
                finals.put("reach", share(views, 0.7, 0.2, seed, 8));
                finals.put("saves", share(views, 0.015, 0.01, seed, 9));
                finals.put("likes", share(views, 0.05, 0.03, seed, 3));
                finals.put("comments", share(views, 0.004, 0.006, seed, 4));
                finals.put("shares", share(views, 0.008, 0.012, seed, 5));
                finals.put("avgWatchSeconds", watchSeconds(seed));
            }
            default -> throw new IllegalStateException("no catalogue for a platform of this post");
        }
        return finals;
    }

    private static Map<String, Double> valuesAt(Map<String, Double> finals, double fraction) {
        Map<String, Double> values = new LinkedHashMap<>();
        finals.forEach((metric, finalValue) -> values.put(metric, valueAt(metric, finalValue, fraction)));
        return values;
    }

    private static double valueAt(String metric, double finalValue, double fraction) {
        if (fraction >= 1.0) {
            return finalValue;
        }
        return switch (metric) {
            // Averages settle from 80 % of their final value up to the final value.
            case "avgViewPercent" -> round(finalValue * (0.8 + 0.2 * fraction), 1);
            case "avgWatchSeconds" -> Math.round(finalValue * (0.8 + 0.2 * fraction));
            default -> Math.round(finalValue * fraction);
        };
    }

    /** The cumulative share of the final value at each offset; non-decreasing, and exactly 1 at +7 d. */
    private static double[] fractions(long seed) {
        double[] fractions = new double[CURVE.length];
        double previous = 0;
        for (int i = 0; i < CURVE.length - 1; i++) {
            double jittered = CURVE[i] * (0.93 + 0.14 * unit(seed, 10 + i));
            previous = Math.max(previous, Math.min(jittered, MAX_BEFORE_FINAL));
            fractions[i] = previous;
        }
        fractions[CURVE.length - 1] = 1.0;
        return fractions;
    }

    private static double watchSeconds(long seed) {
        return Math.round(25 + 60 * unit(seed, 6));
    }

    private static double share(double views, double base, double spread, long seed, int salt) {
        return Math.round(views * (base + spread * unit(seed, salt)));
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /** FNV-1a over the post id: a stable seed that does not depend on the JVM. */
    static long seedOf(String id) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : id.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** SplitMix64 mapped to [0, 1): a deterministic factor per seed and salt. */
    static double unit(long seed, int salt) {
        long z = seed + (salt + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }
}
