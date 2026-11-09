package net.onelitefeather.menura.core.attribution;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The estimate of which posts preceded each first join: the window, the even split and the platform restriction. */
class JoinAttributionTest {

    private static final Instant JOIN = Instant.parse("2026-11-08T12:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(72);
    private static final double DELTA = 1e-9;

    @Test
    void aJoinIsSplitEvenlyAcrossThePostsOfAllChannelsInTheWindow() {
        UUID first = JoinFixture.id(1);
        UUID second = JoinFixture.id(2);
        UUID third = JoinFixture.id(3);
        List<AttributablePost> posts = List.of(
                post(first, "youtube", JOIN.minusSeconds(10 * 3600L)),
                post(second, "tiktok", JOIN.minusSeconds(20 * 3600L)),
                post(third, "twitch", JOIN.minusSeconds(30 * 3600L)));

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(unanswered(JOIN)), posts, WINDOW);

        assertEquals(1.0 / 3, estimates.get(first), DELTA, "the post 10 hours before receives a third");
        assertEquals(1.0 / 3, estimates.get(second), DELTA, "the post 20 hours before receives a third");
        assertEquals(1.0 / 3, estimates.get(third), DELTA, "the post 30 hours before receives a third");
    }

    @Test
    void aPostOutsideTheWindowReceivesNothing() {
        UUID old = JoinFixture.id(1);

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(unanswered(JOIN)),
                List.of(post(old, "youtube", JOIN.minusSeconds(80 * 3600L))), WINDOW);

        assertFalse(estimates.containsKey(old), "a post 80 hours before is outside the 72-hour window");
    }

    @Test
    void aShorterInstallationWindowExcludesAPostFromTheLongerOne() {
        UUID post = JoinFixture.id(1);

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(unanswered(JOIN)),
                List.of(post(post, "youtube", JOIN.minusSeconds(30 * 3600L))), Duration.ofHours(24));

        assertFalse(estimates.containsKey(post), "a post 30 hours before is outside a 24-hour window");
    }

    @Test
    void aPostPublishedAfterTheJoinReceivesNothing() {
        UUID later = JoinFixture.id(1);

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(unanswered(JOIN)),
                List.of(post(later, "youtube", JOIN.plusSeconds(2 * 3600L))), WINDOW);

        assertFalse(estimates.containsKey(later), "a post published after the join did not precede it");
    }

    @Test
    void overlappingWindowsGiveTheFirstPostAllOfTheFirstJoinAndSplitTheSecond() {
        Instant start = Instant.parse("2026-11-06T12:00:00Z");
        UUID postA = JoinFixture.id(1);
        UUID postB = JoinFixture.id(2);
        FirstJoin firstJoin = new FirstJoin(JoinFixture.id(10), start.plusSeconds(10 * 3600L), null);
        FirstJoin secondJoin = new FirstJoin(JoinFixture.id(11), start.plusSeconds(50 * 3600L), null);
        List<AttributablePost> posts = List.of(
                post(postA, "youtube", start),
                post(postB, "tiktok", start.plusSeconds(40 * 3600L)));

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(firstJoin, secondJoin), posts, WINDOW);

        assertEquals(1.5, estimates.get(postA), DELTA, "post A receives the first join and half of the second");
        assertEquals(0.5, estimates.get(postB), DELTA, "post B receives half of the second join only");
        assertEquals(2.0, estimates.values().stream().mapToDouble(Double::doubleValue).sum(), DELTA,
                "the two joins are counted exactly once each");
    }

    @Test
    void aJoinAnsweredWithAPlatformGoesEntirelyToThatPlatformsPost() {
        UUID youtube = JoinFixture.id(1);
        UUID tiktok = JoinFixture.id(2);
        FirstJoin answered = new FirstJoin(JoinFixture.id(10), JOIN, StatedSource.YOUTUBE);

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(answered), List.of(
                post(youtube, "youtube", JOIN.minusSeconds(10 * 3600L)),
                post(tiktok, "tiktok", JOIN.minusSeconds(20 * 3600L))), WINDOW);

        assertEquals(1.0, estimates.get(youtube), DELTA, "the YouTube post receives the whole join");
        assertFalse(estimates.containsKey(tiktok), "the TikTok post receives nothing");
    }

    @Test
    void aJoinAnsweredWithAPlatformWithoutAPostInTheWindowGoesToNoPost() {
        UUID tiktok = JoinFixture.id(2);
        FirstJoin answered = new FirstJoin(JoinFixture.id(10), JOIN, StatedSource.INSTAGRAM);

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(answered),
                List.of(post(tiktok, "tiktok", JOIN.minusSeconds(10 * 3600L))), WINDOW);

        assertTrue(estimates.isEmpty(), "no post receives a join whose platform has no post in the window");
    }

    @Test
    void aJoinAnsweredWithFriendsIsSplitAcrossAllPosts() {
        UUID youtube = JoinFixture.id(1);
        UUID tiktok = JoinFixture.id(2);
        FirstJoin answered = new FirstJoin(JoinFixture.id(10), JOIN, StatedSource.FRIENDS);

        Map<UUID, Double> estimates = JoinAttribution.perPost(List.of(answered), List.of(
                post(youtube, "youtube", JOIN.minusSeconds(10 * 3600L)),
                post(tiktok, "tiktok", JOIN.minusSeconds(20 * 3600L))), WINDOW);

        assertEquals(0.5, estimates.get(youtube), DELTA, "each post receives half of a join answered friends");
        assertEquals(0.5, estimates.get(tiktok), DELTA, "each post receives half of a join answered friends");
    }

    @Test
    void visibilityFollowsTheSeriesOfThePost() {
        AttributablePost plain = post(JoinFixture.id(1), "youtube", JOIN);
        AttributablePost hidden = new AttributablePost(JoinFixture.id(2), "youtube", JOIN, JoinFixture.id(3), "Serie",
                Set.of(net.onelitefeather.menura.core.auth.Role.ADMIN));

        assertTrue(plain.visibleTo(Set.of()), "a post without a series is visible to every role");
        assertFalse(hidden.visibleTo(Set.of(net.onelitefeather.menura.core.auth.Role.VIEWER)),
                "a post of a series hidden from the caller's roles is not visible");
    }

    private static FirstJoin unanswered(Instant at) {
        return new FirstJoin(UUID.nameUUIDFromBytes(at.toString().getBytes()), at, null);
    }

    private static AttributablePost post(UUID id, String platform, Instant publishedAt) {
        return new AttributablePost(id, platform, publishedAt, null, null, null);
    }
}
