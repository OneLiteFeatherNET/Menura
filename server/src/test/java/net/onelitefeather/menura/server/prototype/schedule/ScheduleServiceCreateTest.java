package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules of POST /api/prototype/publications. Each test builds its own harness. */
class ScheduleServiceCreateTest {

    private static final Instant THURSDAY_18_BERLIN = Instant.parse("2026-11-12T17:00:00Z");
    private static final String CTA = "Spiel mit uns: onelitefeather.net";

    /** a-107: a rendered live-migration clip that no seed publication uses. */
    private static final String FREE_CLIP = "a-107";

    private static PublicationCreate create(String assetId, Instant publishAt, String... channelIds) {
        return new PublicationCreate(assetId, List.of(channelIds), publishAt, null);
    }

    private record PublicationCreate(String assetId, List<String> channelIds, Instant publishAt,
                                     ScheduleService.MetadataInput metadata) {
    }

    private static ScheduleService.CreateRequest request(PublicationCreate create) {
        return new ScheduleService.CreateRequest(create.assetId(), create.channelIds(), create.publishAt(),
                create.metadata());
    }

    private static ProblemException refused(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(ProblemException.class, call);
    }

    @Test
    void aClipThatIsNotRenderedIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = refused(() -> h.service.create(request(create("a-110", THURSDAY_18_BERLIN, "yt-de"))));

        assertEquals(422, refused.status());
        assertEquals("Nur fertig gerenderte Clips können eingeplant werden", refused.detail());
    }

    @Test
    void aPublishTimeLessThanFifteenMinutesAheadIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();
        Instant tooSoon = ScheduleHarness.NOW.plusSeconds(14 * 60);

        ProblemException refused = refused(() -> h.service.create(request(create(FREE_CLIP, tooSoon, "yt-de"))));

        assertEquals(422, refused.status());
        assertEquals("publishAt: mindestens 15 Minuten in der Zukunft.", refused.detail());
    }

    @Test
    void aPublishTimeExactlyFifteenMinutesAheadIsAccepted() {
        ScheduleHarness h = ScheduleHarness.seeded();
        Instant exactly = ScheduleHarness.NOW.plusSeconds(15 * 60);

        List<Publication> created = h.service.create(request(create(FREE_CLIP, exactly, "yt-de")));

        assertEquals(exactly, created.get(0).publishAt(), "the lead time is inclusive");
    }

    @Test
    void theSameClipOnTheSameChannelIsRefusedWith409() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de")));

        ProblemException refused = refused(() -> h.service.create(
                request(create(FREE_CLIP, THURSDAY_18_BERLIN.plusSeconds(7 * 86_400), "yt-de"))));

        assertEquals(409, refused.status());
    }

    @Test
    void theSameClipOnAnotherChannelIsAccepted() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de")));

        List<Publication> created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "ig-de")));

        assertEquals("ig-de", created.get(0).channelId(), "the clip goes to the second channel");
    }

    @Test
    void aFailedPublicationDoesNotBlockPlanningTheClipAgain() {
        ScheduleHarness h = ScheduleHarness.seeded();

        List<Publication> created = h.service.create(request(create("a-105", THURSDAY_18_BERLIN, "tt-de")));

        assertEquals(Publication.Status.planned, created.get(0).status(), "the clip can be planned again");
    }

    @Test
    void aBatchWithOneDuplicateChannelStoresNothing() {
        ScheduleHarness h = ScheduleHarness.seeded();
        h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de")));
        int before = h.repository.size();

        // a-107 is already planned on yt-de; the tt-de part alone would have been fine.
        ProblemException refused = refused(() -> h.service.create(
                request(create(FREE_CLIP, THURSDAY_18_BERLIN, "tt-de", "yt-de"))));

        assertEquals(409, refused.status());
        assertEquals(before, h.repository.size(), "a refused batch leaves the store as it was");
    }

    @Test
    void theTitleOfTheDefaultMetadataIsTheTitleOfTheClip() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))).get(0);

        assertEquals("Live-Migration: Speicher synchronisieren", created.metadata().title());
    }

    @Test
    void theDescriptionDefaultIsTheOpeningFormulaTheTitleAndTheCta() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))).get(0);

        assertEquals("Live erklärt: · Live-Migration: Speicher synchronisieren · " + CTA,
                created.metadata().description(), "the three parts, joined by a middle dot");
    }

    @Test
    void theTagDefaultIsTheSeriesHashtagFollowedByTheAssetTagsAsHashtags() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))).get(0);

        assertEquals(List.of("#livemigration", "#live-migration", "#autoscaling"), created.metadata().tags(),
                "the series hashtag first, then the asset tags");
    }

    @Test
    void thePlaylistDefaultIsTheSeriesPlaylistOfTheChannel() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))).get(0);

        assertEquals("https://www.youtube.com/playlist?list=DEMO-LIVE", created.metadata().playlist());
    }

    @Test
    void aChannelWithoutASeriesPlaylistGetsNoPlaylist() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "tt-de"))).get(0);

        assertNull(created.metadata().playlist(), "the series has a playlist on YouTube only");
    }

    @Test
    void explicitMetadataOverridesTheDefaultTitle() {
        ScheduleHarness h = ScheduleHarness.seeded();
        var metadata = new ScheduleService.MetadataInput("Eigener Titel", null, null, null);

        Publication created = h.service.create(new ScheduleService.CreateRequest(FREE_CLIP,
                List.of("yt-de"), THURSDAY_18_BERLIN, metadata)).get(0);

        assertEquals("Eigener Titel", created.metadata().title());
    }

    @Test
    void moreThanFifteenExplicitTagsAreRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();
        List<String> sixteen = java.util.stream.IntStream.range(0, 16).mapToObj(i -> "#tag" + (10 + i)).toList();
        var metadata = new ScheduleService.MetadataInput(null, null, sixteen, null);

        ProblemException refused = refused(() -> h.service.create(new ScheduleService.CreateRequest(FREE_CLIP,
                List.of("yt-de"), THURSDAY_18_BERLIN, metadata)));

        assertEquals(422, refused.status());
    }

    @Test
    void aYoutubePublicationIsNative() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))).get(0);

        assertEquals(Publication.NATIVE, created.mode(), "YouTube schedules the upload itself");
    }

    @Test
    void aTiktokPublicationIsHub() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "tt-de"))).get(0);

        assertEquals(Publication.HUB, created.mode(), "Menura publishes to TikTok itself");
    }

    @Test
    void aNewPublicationStartsPlannedWithAllFiveStepsWaiting() {
        ScheduleHarness h = ScheduleHarness.seeded();

        Publication created = h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))).get(0);

        assertEquals(Publication.Status.planned, created.status(), "a new publication is planned");
        assertEquals(5, created.steps().size(), "five steps");
        assertTrue(created.steps().stream().allMatch(step -> step.state() == Step.StepState.waiting),
                "every step is waiting");
    }

    @Test
    void aFullStoreRefusesANewPublicationWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();
        int room = PublicationRepository.CAPACITY - h.repository.size();
        h.repository.insertAll(java.util.stream.IntStream.range(0, room)
                .mapToObj(i -> ScheduleHarness.row("fill-" + i)).toList());

        ProblemException refused = refused(() -> h.service.create(request(create(FREE_CLIP, THURSDAY_18_BERLIN, "yt-de"))));

        assertEquals(422, refused.status());
        assertEquals("Es sind höchstens 1000 Veröffentlichungen erlaubt.", refused.detail());
    }
}
