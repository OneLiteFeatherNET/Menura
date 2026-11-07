package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonRulesTest {

    private static final List<Model.Series> SERIES = DemoSeed.series();
    private static final String WEEK = "2026-W45";

    private static Model.Post viewed(String id, String seriesId, long engaged, Double avg, double estimate) {
        return new Model.Post(id, "Titel " + id, "yt-de", "youtube", "2026-11-03T16:00:00Z", List.of(), seriesId,
                false, null, engaged, avg, estimate, true);
    }

    private static Model.SeriesComparison row(List<Model.SeriesComparison> rows, String seriesId) {
        return rows.stream().filter(r -> r.seriesId().equals(seriesId)).findFirst().orElseThrow();
    }

    @Test
    void sortsByEngagedViewsDescendingWhateverTheNumberOfPosts() {
        // survival has two posts with 1 000 engaged views in total, live-migration one post with 1 500.
        List<Model.Post> posts = List.of(
                viewed("a", "survival", 500, null, 0),
                viewed("b", "survival", 500, null, 0),
                viewed("c", "live-migration", 1500, null, 0),
                viewed("d", "k8s-30s", 1200, null, 0));

        List<String> order = ComparisonRules.compare(WEEK, SERIES, posts).stream()
                .map(Model.SeriesComparison::seriesId).toList();

        assertEquals(List.of("live-migration", "k8s-30s", "survival", "wir-bauen-olf"), order);
    }

    @Test
    void theAverageViewPercentIgnoresPostsWithoutAPlatformValue() {
        List<Model.Post> posts = List.of(
                viewed("a", "k8s-30s", 100, 50.0, 0),
                viewed("b", "k8s-30s", 100, null, 0),
                viewed("c", "k8s-30s", 100, 70.0, 0));

        assertEquals(60.0, row(ComparisonRules.compare(WEEK, SERIES, posts), "k8s-30s").avgViewPercent(), 1e-9);
    }

    @Test
    void aSeriesWithoutPostsInTheWeekIsListedWithZeroesAndNoAverage() {
        Model.SeriesComparison row = row(ComparisonRules.compare(WEEK, SERIES, List.of()), "survival");

        assertEquals(0, row.posts());
        assertEquals(0, row.engagedViews());
        assertNull(row.avgViewPercent(), "no value is shown as null, never as zero");
        assertEquals(0.0, row.estimatedFirstJoins(), 1e-9);
        assertTrue(row.estimate(), "estimates are always marked as estimates");
    }

    @Test
    void onlyPostsOfTheRequestedWeekAreCounted() {
        List<Model.Post> posts = List.of(
                new Model.Post("old", "t", "yt-de", "youtube", "2026-10-27T17:00:00Z", List.of(), "k8s-30s",
                        false, 1, 9000, null, 0, true),
                viewed("new", "k8s-30s", 100, null, 0));

        Model.SeriesComparison row = row(ComparisonRules.compare(WEEK, SERIES, posts), "k8s-30s");

        assertEquals(1, row.posts());
        assertEquals(100, row.engagedViews());
    }

    @Test
    void theEstimatedFirstJoinsOfTheWeekAreTheSumOfItsPosts() {
        List<Model.Post> posts = List.of(
                viewed("a", "k8s-30s", 100, null, 0.5),
                viewed("b", "k8s-30s", 100, null, 0.25));

        assertEquals(0.75, row(ComparisonRules.compare(WEEK, SERIES, posts), "k8s-30s").estimatedFirstJoins(), 1e-9);
    }
}
