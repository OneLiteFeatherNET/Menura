package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GET /api/prototype/publications: published posts, filters and order. Each test builds its own harness. */
class ScheduleServiceListTest {

    private static List<String> idsOf(List<Publication> publications) {
        return publications.stream().map(Publication::id).toList();
    }

    @Test
    void publishedPostsAreListedAsPublishedWithTheirPostId() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication post = h.service.list(null, null, null, null, "published").stream()
                .filter(p -> "pub-p-41".equals(p.id())).findFirst().orElseThrow();

        assertEquals(Publication.Status.published, post.status(), "a v1 post is published");
        assertEquals("p-41", post.postId(), "the link to the v1 post");
        assertNull(post.assetId(), "a v1 post has no clip in the pool");
    }

    @Test
    void everyStepOfAPublishedPostIsDone() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication post = h.service.list(null, null, "yt-de", null, "published").get(0);

        assertTrue(post.steps().stream().allMatch(step -> step.state() == Step.StepState.done),
                "upload, schedule, metadata, publish and metrics are all done");
    }

    @Test
    void aPublishedYoutubePostIsNativeAndATiktokPostIsHub() {
        ScheduleHarness h = ScheduleHarness.seeded();
        List<Publication> published = h.service.list(null, null, null, null, "published");

        assertEquals(Publication.NATIVE, published.stream().filter(p -> "pub-p-41".equals(p.id())).findFirst()
                .orElseThrow().mode(), "YouTube");
        assertEquals(Publication.HUB, published.stream().filter(p -> "pub-p-42".equals(p.id())).findFirst()
                .orElseThrow().mode(), "TikTok");
    }

    @Test
    void thePublicationsAreSortedByPublishTime() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Instant> times = h.service.list(null, null, null, null, null).stream().map(Publication::publishAt).toList();

        assertEquals(times.stream().sorted().toList(), times, "oldest first");
    }

    @Test
    void theChannelFilterKeepsOnlyThatChannel() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> onTiktok = h.service.list(null, null, "tt-de", null, null);

        assertTrue(onTiktok.stream().allMatch(p -> "tt-de".equals(p.channelId())), "only TikTok publications");
        assertTrue(onTiktok.stream().anyMatch(p -> "pub-p-42".equals(p.id())), "the TikTok post is among them");
    }

    @Test
    void theSeriesFilterKeepsOnlyThatSeries() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> survival = h.service.list(null, null, null, "survival", null);

        assertFalse(survival.isEmpty(), "the survival series has publications");
        assertTrue(survival.stream().allMatch(p -> "survival".equals(p.seriesId())), "only survival publications");
    }

    @Test
    void theStatusFilterKeepsOnlyThatStatus() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> scheduled = h.service.list(null, null, null, null, "scheduled");

        assertEquals(4, scheduled.size(), "four natively scheduled YouTube uploads in the seed");
        assertTrue(scheduled.stream().allMatch(p -> p.status() == Publication.Status.scheduled), "only scheduled");
    }

    @Test
    void theFromBoundIsInclusiveAndTheToBoundIsExclusive() {
        ScheduleHarness h = ScheduleHarness.seeded();
        Instant seeded = h.service.list(null, null, "yt-de", null, "scheduled").get(0).publishAt();

        List<Publication> from = h.service.list(seeded.toString(), null, "yt-de", null, "scheduled");
        List<Publication> until = h.service.list(null, seeded.toString(), "yt-de", null, "scheduled");

        assertTrue(idsOf(from).contains(h.idOf("a-104", "yt-de")), "a publication at 'from' is included");
        assertFalse(idsOf(until).contains(h.idOf("a-104", "yt-de")), "a publication at 'to' is excluded");
    }

    @Test
    void anUnknownStatusIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.list(null, null, null, null, "geplant"));

        assertEquals(422, refused.status());
    }

    @Test
    void aMalformedFromIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.list("8. November", null, null, null, null));

        assertEquals(422, refused.status());
    }

    @Test
    void thePlatformCapabilitiesSayYoutubeSchedulesNativelyAndTheOthersDoNot() {
        ScheduleHarness h = ScheduleHarness.seeded();

        assertTrue(h.service.platforms().get("youtube").nativeSchedule(), "YouTube");
        assertFalse(h.service.platforms().get("tiktok").nativeSchedule(), "TikTok");
        assertFalse(h.service.platforms().get("instagram").nativeSchedule(), "Instagram");
    }
}
