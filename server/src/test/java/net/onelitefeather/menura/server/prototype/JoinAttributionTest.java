package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinAttributionTest {

    private static final Instant JOIN = Instant.parse("2026-11-06T10:00:00Z");

    private static Model.JoinEvent answered(String answer, Instant at) {
        return new Model.JoinEvent(answer, true, at);
    }

    private static Model.JoinEvent unanswered(Instant at) {
        return new Model.JoinEvent(null, true, at);
    }

    private static Model.Post on(String id, String platform, String publishedAt) {
        return Fixtures.onPlatform(id, platform, publishedAt);
    }

    private static Map<String, Double> estimates(List<Model.Post> posts, List<Model.JoinEvent> joins) {
        return JoinAttribution.estimates(posts, joins);
    }

    // Estimates: the split over posts

    @Test
    void anUnansweredJoinIsSplitEvenlyAcrossAllPostsOfItsWindowWhateverTheirPlatform() {
        List<Model.Post> posts = List.of(
                on("a", "youtube", "2026-11-04T10:00:00Z"),
                on("b", "tiktok", "2026-11-05T10:00:00Z"));

        Map<String, Double> result = estimates(posts, List.of(unanswered(JOIN)));

        assertEquals(0.5, result.get("a"), 1e-9);
        assertEquals(0.5, result.get("b"), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"friends", "other"})
    void aJoinNamingFriendsOrOtherIsSplitAcrossAllPostsOfItsWindow(String answer) {
        List<Model.Post> posts = List.of(
                on("a", "youtube", "2026-11-04T10:00:00Z"),
                on("b", "tiktok", "2026-11-05T10:00:00Z"));

        Map<String, Double> result = estimates(posts, List.of(answered(answer, JOIN)));

        assertEquals(0.5, result.get("a"), 1e-9);
        assertEquals(0.5, result.get("b"), 1e-9);
    }

    @Test
    void aJoinNamingAPlatformIsSplitOnlyAcrossThatPlatformsPostsOfItsWindow() {
        List<Model.Post> posts = List.of(
                on("a", "youtube", "2026-11-04T10:00:00Z"),
                on("b", "tiktok", "2026-11-05T10:00:00Z"));

        Map<String, Double> result = estimates(posts, List.of(answered("tiktok", JOIN)));

        assertEquals(1.0, result.get("b"), 1e-9);
        assertTrue(!result.containsKey("a"), "a youtube post does not share a tiktok join");
    }

    @Test
    void aJoinNamingAPlatformWithoutAPostInItsWindowGivesNoPostAnEstimate() {
        List<Model.Post> posts = List.of(on("a", "youtube", "2026-11-05T10:00:00Z"));

        assertTrue(estimates(posts, List.of(answered("tiktok", JOIN))).isEmpty(),
                "the join counts for the platform only, not for the youtube post");
    }

    @Test
    void aJoinWithoutAnyPostInItsWindowIsNotAttributed() {
        List<Model.Post> posts = List.of(on("old", "youtube", "2026-11-01T10:00:00Z"));

        assertTrue(estimates(posts, List.of(unanswered(JOIN))).isEmpty());
    }

    @Test
    void overlappingWindowsAddUpPerPost() {
        List<Model.Post> posts = List.of(
                on("a", "youtube", "2026-11-04T10:00:00Z"),
                on("b", "youtube", "2026-11-05T10:00:00Z"));
        List<Model.JoinEvent> joins = List.of(
                unanswered(Instant.parse("2026-11-05T11:00:00Z")),
                unanswered(Instant.parse("2026-11-07T10:00:00Z")));

        Map<String, Double> result = estimates(posts, joins);

        assertEquals(1.0, result.get("a"), 1e-9, "a is in both windows: half of each join");
        assertEquals(1.0, result.get("b"), 1e-9);
    }

    // Estimates: the window

    @Test
    void aPostExactlySeventyTwoHoursBeforeTheJoinIsInTheWindow() {
        List<Model.Post> posts = List.of(on("edge", "youtube", "2026-11-03T10:00:00Z"));

        assertEquals(1.0, estimates(posts, List.of(unanswered(JOIN))).get("edge"), 1e-9);
    }

    @Test
    void aPostOneSecondOlderThanSeventyTwoHoursIsNotInTheWindow() {
        List<Model.Post> posts = List.of(on("old", "youtube", "2026-11-03T09:59:59Z"));

        assertTrue(estimates(posts, List.of(unanswered(JOIN))).isEmpty());
    }

    @Test
    void aPostPublishedAfterTheJoinIsNotCounted() {
        List<Model.Post> posts = List.of(on("later", "youtube", "2026-11-06T10:00:01Z"));

        assertTrue(estimates(posts, List.of(unanswered(JOIN))).isEmpty());
    }

    @Test
    void aRepeatJoinIsIgnoredAndGivesNoEstimate() {
        List<Model.Post> posts = List.of(on("a", "youtube", "2026-11-05T10:00:00Z"));
        Model.JoinEvent repeat = new Model.JoinEvent("youtube", false, JOIN);

        assertTrue(estimates(posts, List.of(repeat)).isEmpty());
    }

    // Weekly counts

    @Test
    void weeklyCountsFirstJoinsAnsweredJoinsAndTheAnswerRatePerIsoWeek() {
        List<Model.JoinEvent> joins = List.of(
                answered("youtube", Instant.parse("2026-10-27T18:00:00Z")),
                unanswered(Instant.parse("2026-11-01T08:00:00Z")),
                answered("tiktok", Instant.parse("2026-11-05T10:00:00Z")),
                unanswered(Instant.parse("2026-11-06T09:00:00Z")),
                new Model.JoinEvent("youtube", false, Instant.parse("2026-11-05T11:00:00Z")));

        List<Model.WeeklyAttribution> rows = JoinAttribution.weekly(joins);

        assertEquals(List.of("2026-W44", "2026-W45"), rows.stream().map(Model.WeeklyAttribution::week).toList());
        assertEquals(List.of(2, 2), rows.stream().map(Model.WeeklyAttribution::firstJoins).toList());
        assertEquals(List.of(1, 1), rows.stream().map(Model.WeeklyAttribution::answered).toList());
        assertEquals(List.of(0.5, 0.5), rows.stream().map(Model.WeeklyAttribution::answerRate).toList());
    }

    @Test
    void bySourceListsAllSevenSourcesInTheFixedOrderIncludingTheZeros() {
        List<Model.WeeklyAttribution> rows = JoinAttribution.weekly(List.of(
                answered("youtube", Instant.parse("2026-10-27T18:00:00Z"))));

        assertEquals(List.of(
                new Model.SourceCount("youtube", 1),
                new Model.SourceCount("tiktok", 0),
                new Model.SourceCount("instagram", 0),
                new Model.SourceCount("twitch", 0),
                new Model.SourceCount("friends", 0),
                new Model.SourceCount("other", 0),
                new Model.SourceCount("unanswered", 0)), rows.get(0).bySource());
    }

    @Test
    void theAnswerRateIsRoundedHalfUpToTwoDecimals() {
        Instant base = Instant.parse("2026-11-02T10:00:00Z");
        List<Model.JoinEvent> joins = new ArrayList<>();
        joins.add(answered("youtube", base));
        for (int second = 1; second <= 7; second++) {
            joins.add(unanswered(base.plusSeconds(second)));
        }

        Model.WeeklyAttribution week = JoinAttribution.weekly(joins).get(0);

        assertEquals(8, week.firstJoins());
        assertEquals(0.13, week.answerRate(), 1e-9, "1 of 8 is 0.125, which rounds half up to 0.13");
    }
}
