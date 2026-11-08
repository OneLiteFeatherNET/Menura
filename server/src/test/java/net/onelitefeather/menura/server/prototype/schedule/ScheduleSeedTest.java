package net.onelitefeather.menura.server.prototype.schedule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo publications of the seed. Each test builds its own harness. */
class ScheduleSeedTest {

    @Test
    void theSeedHoldsTenPlannedOrScheduledPublications() {
        ScheduleHarness h = ScheduleHarness.seeded();

        long open = h.repository.all().stream()
                .filter(p -> p.status() == Publication.Status.planned || p.status() == Publication.Status.scheduled)
                .count();

        assertEquals(10, open, "ten planned or scheduled publications");
    }

    @Test
    void theSeedHoldsExactlyOneFailedUpload() {
        ScheduleHarness h = ScheduleHarness.seeded();

        long failed = h.repository.all().stream().filter(p -> p.status() == Publication.Status.failed).count();

        assertEquals(1, failed, "one failed upload");
    }

    @Test
    void theFailedUploadIsTiktokAndNamesTheMissingAuditAsItsDetail() {
        ScheduleHarness h = ScheduleHarness.seeded();
        Publication failed = h.repository.all().stream().filter(p -> p.status() == Publication.Status.failed)
                .findFirst().orElseThrow();

        assertEquals("tt-de", failed.channelId(), "the failed upload is on TikTok");
        assertEquals("Ohne API-Audit nur privat veröffentlicht", failed.steps().get(0).detail(),
                "the upload step carries the audit detail");
        assertEquals(Step.StepState.failed, failed.steps().get(0).state(), "and it is failed");
    }

    @Test
    void theYoutubeSeedsAreScheduledNativelyWithTheFirstThreeStepsDone() {
        ScheduleHarness h = ScheduleHarness.seeded();
        Publication scheduled = h.repository.all().stream()
                .filter(p -> p.status() == Publication.Status.scheduled).findFirst().orElseThrow();

        assertEquals(Publication.NATIVE, scheduled.mode(), "YouTube is native");
        assertEquals(List.of(Step.StepState.done, Step.StepState.done, Step.StepState.done,
                        Step.StepState.waiting, Step.StepState.waiting),
                scheduled.steps().stream().map(Step::state).toList(),
                "upload, schedule and metadata are done; publish and metrics wait");
    }

    @Test
    void theTiktokAndInstagramSeedsArePlannedAndHub() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> planned = h.repository.all().stream()
                .filter(p -> "tt-de".equals(p.channelId()) || "ig-de".equals(p.channelId()))
                .filter(p -> p.status() == Publication.Status.planned).toList();

        assertFalse(planned.isEmpty(), "some TikTok and Instagram publications are planned");
        assertTrue(planned.stream().allMatch(p -> Publication.HUB.equals(p.mode())), "both are hub");
    }

    @Test
    void theRenderedSeriesClipsA107AndA109AreLeftForThePlanEndpoint() {
        ScheduleHarness h = ScheduleHarness.seeded();

        assertFalse(h.repository.hasActive("a-107"), "a-107 is unscheduled");
        assertFalse(h.repository.hasActive("a-109"), "a-109 is unscheduled");
    }

    @Test
    void theSeedIsPlacedRelativeToTheInjectedClock() {
        ScheduleHarness h = ScheduleHarness.at(java.time.Instant.parse("2026-12-01T12:00:00Z"));

        assertTrue(h.repository.all().stream().allMatch(p -> p.publishAt().isAfter(h.clock.instant())),
                "every open publication lies after the clock");
    }
}
