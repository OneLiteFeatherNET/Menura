package net.onelitefeather.menura.server.prototype.analytics;

import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.prototype.Model;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules of the aggregation on hand-made posts. Every test passes its own clock and posts, so nothing is shared.
 * At +7 d the snapshot holds the post's engaged views as views and its average as the average, which keeps the expected
 * numbers exact.
 */
class AggregationTest {

    private static final Instant LATER = Instant.parse("2026-12-31T00:00:00Z");
    private static final List<Model.Channel> CHANNELS = List.of(
            new Model.Channel("yt-de", "youtube", "YouTube"),
            new Model.Channel("tt-de", "tiktok", "TikTok"),
            new Model.Channel("ig-de", "instagram", "Instagram"));

    private static Model.Post youtube(String id, String publishedAt, long engagedViews, Double avg) {
        return new Model.Post(id, "Titel " + id, "yt-de", "youtube", publishedAt, List.of(), null, true, null,
                engagedViews, avg, 0.0, false);
    }

    private static Model.Post tiktok(String id, String publishedAt, long engagedViews) {
        return new Model.Post(id, "Titel " + id, "tt-de", "tiktok", publishedAt, List.of(), null, true, null,
                engagedViews, null, 0.0, false);
    }

    private static Aggregation.Result aggregate(List<Model.Post> posts, String metric, String groupBy, String offset,
                                                Instant now) {
        Aggregation.Selection selection = Aggregation.select(CHANNELS, metric, null, offset, null, null);
        List<Aggregation.Contribution> contributions = Aggregation.contributions(selection, posts, now);
        return new Aggregation.Result(metric, groupBy, selection.offset().label(),
                Aggregation.rows(selection, Aggregation.GroupBy.parse(groupBy), contributions, List.of()),
                Aggregation.missing(selection));
    }

    @Test
    void medianByPublishHourUsesBerlinTimeNotUtc() {
        // 23:30 UTC on 5 Nov is 00:30 in Berlin on 6 Nov; 23:10 UTC on 6 Nov is 00:10 in Berlin on 7 Nov.
        List<Model.Post> posts = List.of(
                youtube("a", "2026-11-05T23:30:00Z", 100, null),
                youtube("b", "2026-11-06T23:10:00Z", 300, null),
                youtube("c", "2026-11-07T10:00:00Z", 1000, null));

        Aggregation.Result result = aggregate(posts, "views", "publishHour", "+7d", LATER);

        assertEquals(List.of("0", "11"), result.rows().stream().map(Aggregation.Row::key).toList());
        assertEquals(200.0, result.rows().get(0).value(), "median of 100 and 300 near midnight Berlin");
        assertEquals(2, result.rows().get(0).posts());
        assertEquals(1000.0, result.rows().get(1).value());
    }

    @Test
    void weekdayGroupsUseTheBerlinDay() {
        List<Model.Post> posts = List.of(
                youtube("a", "2026-11-05T23:30:00Z", 100, null),
                youtube("b", "2026-11-06T23:10:00Z", 300, null));

        Aggregation.Result result = aggregate(posts, "views", "weekday", "+7d", LATER);

        assertEquals(List.of("FRIDAY", "SATURDAY"), result.rows().stream().map(Aggregation.Row::key).toList());
    }

    @Test
    void dayGroupsUseTheBerlinDateOfThePublishTime() {
        List<Model.Post> posts = List.of(youtube("a", "2026-11-05T23:30:00Z", 100, null));

        Aggregation.Result result = aggregate(posts, "views", "day", "+7d", LATER);

        assertEquals("2026-11-06", result.rows().get(0).key());
    }

    @Test
    void countMetricsAreSummedPerGroup() {
        List<Model.Post> posts = List.of(
                youtube("a", "2026-10-01T10:00:00Z", 1000, 70.2),
                youtube("b", "2026-10-01T12:00:00Z", 500, 48.2));

        Aggregation.Result result = aggregate(posts, "views", "channel", "+7d", LATER);

        assertEquals(1500.0, result.rows().get(0).value());
        assertEquals(2, result.rows().get(0).posts());
    }

    @Test
    void averageMetricsAreMeanedPerGroup() {
        List<Model.Post> posts = List.of(
                youtube("a", "2026-10-01T10:00:00Z", 1000, 70.2),
                youtube("b", "2026-10-01T12:00:00Z", 500, 48.2));

        Aggregation.Result result = aggregate(posts, "avgViewPercent", "channel", "+7d", LATER);

        assertEquals(59.2, result.rows().get(0).value(), 0.001);
    }

    @Test
    void offsetExcludesPostsYoungerThanTheOffset() {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");
        List<Model.Post> posts = List.of(
                youtube("young", "2026-11-08T06:00:00Z", 100, null),
                youtube("exactly", "2026-11-07T12:00:00Z", 100, null),
                youtube("old", "2026-11-06T12:00:00Z", 100, null));

        Aggregation.Result result = aggregate(posts, "views", "channel", "+24h", now);

        assertEquals(2, result.rows().get(0).posts(), "the post published 24 h ago counts, the one of 6 h does not");
    }

    @Test
    void offsetInTheFutureOfTheClockIsNotServed() {
        Instant now = Instant.parse("2026-11-08T12:00:00Z");
        Model.Post post = youtube("p", "2026-11-06T17:00:00Z", 100, null);

        assertTrue(Aggregation.snapshotAt(post, Snapshot.Offset.H24, now).isPresent());
        assertFalse(Aggregation.snapshotAt(post, Snapshot.Offset.H48, now).isPresent());
    }

    @Test
    void channelsWhosePlatformLacksTheMetricAreListedAsMissing() {
        Aggregation.Selection selection = Aggregation.select(CHANNELS, "engagedViews", null, "+7d", null, null);

        assertEquals(List.of("tt-de", "ig-de"), Aggregation.missing(selection));
    }

    @Test
    void aTikTokPostContributesNothingToEngagedViewsNotAZero() {
        List<Model.Post> posts = List.of(
                youtube("a", "2026-10-01T10:00:00Z", 1000, 70.2),
                tiktok("t", "2026-10-01T11:00:00Z", 2100));

        Aggregation.Result result = aggregate(posts, "engagedViews", "channel", "+7d", LATER);

        assertEquals(List.of("yt-de"), result.rows().stream().map(Aggregation.Row::key).toList());
        assertEquals(List.of("tt-de", "ig-de"), result.missing());
    }

    @Test
    void heatmapHasOnlyCellsWithPostsAndTheirMedian() {
        List<Model.Post> posts = List.of(
                youtube("a", "2026-11-05T23:30:00Z", 100, null),
                youtube("b", "2026-11-06T23:10:00Z", 300, null),
                youtube("c", "2026-11-07T10:00:00Z", 1000, null));
        Aggregation.Selection selection = Aggregation.select(CHANNELS, "views", null, "+7d", null, null);

        Aggregation.Heatmap heatmap = Aggregation.heatmap(selection,
                Aggregation.contributions(selection, posts, LATER));

        assertEquals(3, heatmap.cells().size());
        Aggregation.Cell friday = heatmap.cells().get(0);
        assertEquals("FRIDAY", friday.weekday());
        assertEquals(0, friday.hour());
        assertEquals(100.0, friday.value());
        assertEquals(1, friday.posts());
    }

    @Test
    void unknownMetricIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> Aggregation.select(CHANNELS, "clicks", null, "+24h", null, null));

        assertEquals(422, refused.status());
    }

    @Test
    void missingMetricIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> Aggregation.select(CHANNELS, null, null, "+24h", null, null));

        assertEquals(422, refused.status());
    }

    @Test
    void unknownOffsetIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> Aggregation.select(CHANNELS, "views", null, "+3h", null, null));

        assertEquals(422, refused.status());
    }

    @Test
    void unknownGroupByIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> Aggregation.GroupBy.parse("month"));

        assertEquals(422, refused.status());
    }

    @Test
    void unknownChannelIsRefusedWith422() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> Aggregation.select(CHANNELS, "views", "yt-de,xx-de", "+24h", null, null));

        assertEquals(422, refused.status());
    }

    @Test
    void anOffsetIsDefaultedToTwentyFourHours() {
        Aggregation.Selection selection = Aggregation.select(CHANNELS, "views", null, null, null, null);

        assertEquals(Snapshot.Offset.H24, selection.offset());
    }
}
