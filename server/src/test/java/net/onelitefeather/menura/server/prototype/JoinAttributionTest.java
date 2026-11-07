package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinAttributionTest {

    private static final Instant JOIN = Instant.parse("2026-11-06T10:00:00Z");

    private final List<Model.Channel> channels = DemoSeed.channels();

    private static Model.JoinEvent join(String host, Instant at) {
        return new Model.JoinEvent(host, true, at);
    }

    private Map<String, Double> estimates(List<Model.Post> posts, List<Model.JoinEvent> joins) {
        return JoinAttribution.estimates(posts, channels, joins);
    }

    @Test
    void aFirstJoinIsSplitEvenlyAcrossTheChannelPostsOfItsWindow() {
        List<Model.Post> posts = List.of(
                Fixtures.post("a", "yt-de", "2026-11-04T10:00:00Z", null, false),
                Fixtures.post("b", "yt-de", "2026-11-05T10:00:00Z", null, false));

        Map<String, Double> result = estimates(posts, List.of(join("yt.onelitefeather.net", JOIN)));

        assertEquals(0.5, result.get("a"), 1e-9);
        assertEquals(0.5, result.get("b"), 1e-9);
    }

    @Test
    void aPostExactlySeventyTwoHoursBeforeTheJoinIsInTheWindowAndOneSecondOlderIsNot() {
        List<Model.Post> posts = List.of(
                Fixtures.post("edge", "yt-de", "2026-11-03T10:00:00Z", null, false),
                Fixtures.post("old", "yt-de", "2026-11-03T09:59:59Z", null, false));

        Map<String, Double> result = estimates(posts, List.of(join("yt.onelitefeather.net", JOIN)));

        assertEquals(1.0, result.get("edge"), 1e-9);
        assertTrue(!result.containsKey("old"), "a post older than 72 h is outside the window");
    }

    @Test
    void aPostPublishedAfterTheJoinIsNotCounted() {
        List<Model.Post> posts = List.of(Fixtures.post("later", "yt-de", "2026-11-06T10:00:01Z", null, false));

        assertTrue(estimates(posts, List.of(join("yt.onelitefeather.net", JOIN))).isEmpty());
    }

    @Test
    void overlappingWindowsAddUpPerPost() {
        List<Model.Post> posts = List.of(
                Fixtures.post("a", "yt-de", "2026-11-04T10:00:00Z", null, false),
                Fixtures.post("b", "yt-de", "2026-11-05T10:00:00Z", null, false));
        List<Model.JoinEvent> joins = List.of(
                join("yt.onelitefeather.net", Instant.parse("2026-11-05T11:00:00Z")),
                join("yt.onelitefeather.net", Instant.parse("2026-11-07T10:00:00Z")));

        Map<String, Double> result = estimates(posts, joins);

        assertEquals(1.0, result.get("a"), 1e-9, "a is in both windows: half of each join");
        assertEquals(1.0, result.get("b"), 1e-9);
    }

    @Test
    void aJoinWithoutAnyPostInItsWindowIsNotAttributed() {
        List<Model.Post> posts = List.of(Fixtures.post("old", "yt-de", "2026-11-01T10:00:00Z", null, false));

        assertTrue(estimates(posts, List.of(join("yt.onelitefeather.net", JOIN))).isEmpty());
    }

    @Test
    void aDirectJoinIsNotAttributedToAnyPost() {
        List<Model.Post> posts = List.of(Fixtures.post("a", "yt-de", "2026-11-05T10:00:00Z", null, false));

        assertTrue(estimates(posts, List.of(join("unknown.example", JOIN))).isEmpty());
    }

    @Test
    void aJoinThroughAnotherChannelDoesNotCountForThisChannelsPosts() {
        List<Model.Post> posts = List.of(Fixtures.post("a", "yt-de", "2026-11-05T10:00:00Z", null, false));

        assertTrue(estimates(posts, List.of(join("tt.onelitefeather.net", JOIN))).isEmpty());
    }

    @Test
    void aRepeatJoinIsNotAFirstJoinAndIsIgnored() {
        List<Model.Post> posts = List.of(Fixtures.post("a", "yt-de", "2026-11-05T10:00:00Z", null, false));
        Model.JoinEvent repeat = new Model.JoinEvent("yt.onelitefeather.net", false, JOIN);

        assertTrue(estimates(posts, List.of(repeat)).isEmpty());
    }

    @Test
    void weeklyCountsMeasuredFirstJoinsPerChannelAndDirectPerIsoWeek() {
        List<Model.JoinEvent> joins = List.of(
                join("yt.onelitefeather.net", Instant.parse("2026-10-27T18:00:00Z")),
                join("unknown.example", Instant.parse("2026-11-01T08:00:00Z")),
                join("yt.onelitefeather.net", Instant.parse("2026-11-05T10:00:00Z")),
                new Model.JoinEvent("yt.onelitefeather.net", false, Instant.parse("2026-11-05T11:00:00Z")));

        List<Model.WeeklyAttribution> rows = JoinAttribution.weekly(channels, joins);

        assertEquals(List.of(
                new Model.WeeklyAttribution("2026-W44", "youtube", "yt-de", 1),
                new Model.WeeklyAttribution("2026-W44", "direct", null, 1),
                new Model.WeeklyAttribution("2026-W45", "youtube", "yt-de", 1)), rows);
    }
}
