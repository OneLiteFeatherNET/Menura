package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The rules of POST /api/prototype/series/{id}/plan. Each test builds its own harness. */
class ScheduleServicePlanTest {

    /** The live-migration series: Thursday 18:00 Berlin, which is 17:00 UTC in winter time. */
    private static final String SERIES = "live-migration";
    private static final Instant THURSDAY_NOV_12 = Instant.parse("2026-11-12T17:00:00Z");
    private static final Instant THURSDAY_NOV_19 = Instant.parse("2026-11-19T17:00:00Z");

    private static ScheduleService.PlanRequest plan(int count, String... channelIds) {
        return new ScheduleService.PlanRequest(List.of(channelIds), count);
    }

    @Test
    void planPutsTheOnlyFreeRenderedClipOfTheSeriesOnTheNextFreeSlot() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> planned = h.service.plan(SERIES, plan(1, "yt-de"));

        assertEquals(1, planned.size(), "one clip, one channel");
        assertEquals("a-107", planned.get(0).assetId(), "a-107 is the only unscheduled rendered live-migration clip");
        assertEquals(THURSDAY_NOV_12, planned.get(0).publishAt(), "the first Thursday 18:00 Berlin after now");
    }

    @Test
    void planTakesTheOldestUnscheduledClipFirst() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.saveRenderedClip("a-200", SERIES, "2026-10-30T10:00:00Z");

        List<Publication> planned = h.service.plan(SERIES, plan(2, "yt-de"));

        assertEquals(List.of("a-200", "a-107"), planned.stream().map(Publication::assetId).toList(),
                "the clip created on 30 Oct is older than a-107 of 1 Nov");
    }

    @Test
    void planGivesEachClipItsOwnSlotInOrder() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.saveRenderedClip("a-200", SERIES, "2026-10-30T10:00:00Z");

        List<Publication> planned = h.service.plan(SERIES, plan(2, "yt-de"));

        assertEquals(List.of(THURSDAY_NOV_12, THURSDAY_NOV_19),
                planned.stream().map(Publication::publishAt).toList(), "one clip per slot, oldest slot first");
    }

    @Test
    void planSkipsASlotTakenOnTheChannel() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.service.create(new ScheduleService.CreateRequest("a-107", List.of("yt-de"), THURSDAY_NOV_12, null));
        h.saveRenderedClip("a-200", SERIES, "2026-10-30T10:00:00Z");

        List<Publication> planned = h.service.plan(SERIES, plan(1, "yt-de"));

        assertEquals(THURSDAY_NOV_19, planned.get(0).publishAt(),
                "the Thursday of 12 Nov is taken on YouTube, so the clip goes to 19 Nov");
    }

    @Test
    void planSkipsASlotTakenOnAnyRequestedChannel() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.service.create(new ScheduleService.CreateRequest("a-107", List.of("tt-de"), THURSDAY_NOV_12, null));
        h.saveRenderedClip("a-200", SERIES, "2026-10-30T10:00:00Z");

        List<Publication> planned = h.service.plan(SERIES, plan(1, "yt-de", "tt-de"));

        assertEquals(THURSDAY_NOV_19, planned.get(0).publishAt(),
                "a slot taken on one of the channels is not used for any of them");
    }

    @Test
    void planCreatesOnePublicationPerChannelForTheSameClip() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> planned = h.service.plan(SERIES, plan(1, "yt-de", "tt-de"));

        assertEquals(List.of("yt-de", "tt-de"), planned.stream().map(Publication::channelId).toList(),
                "one publication for each channel");
        assertEquals(List.of("a-107", "a-107"), planned.stream().map(Publication::assetId).toList(),
                "the same clip on both channels");
    }

    @Test
    void planWithoutAnyUnscheduledClipIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();

        // k8s-30s has two rendered clips and both are scheduled in the seed.
        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.plan("k8s-30s", plan(1, "yt-de")));

        assertEquals(422, refused.status());
    }

    @Test
    void aCountBelowOneIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.plan(SERIES, plan(0, "yt-de")));

        assertEquals(422, refused.status());
        assertEquals("count: erlaubt sind 1 bis 10.", refused.detail());
    }

    @Test
    void aCountAboveTenIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.plan(SERIES, plan(11, "yt-de")));

        assertEquals(422, refused.status());
    }

    @Test
    void anUnknownSeriesIsNotFound() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.plan("gibt-es-nicht", plan(1, "yt-de")));

        assertEquals(404, refused.status());
    }

    @Test
    void aPlannedSlotIsEighteenBerlinTimeInWinterTime() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> planned = h.service.plan(SERIES, plan(1, "yt-de"));

        assertEquals(17, planned.get(0).publishAt().atZone(java.time.ZoneOffset.UTC).getHour(),
                "18:00 Berlin in winter time is 17:00 UTC");
    }
}
