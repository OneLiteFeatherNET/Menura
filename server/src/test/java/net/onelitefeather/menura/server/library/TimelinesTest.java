package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.library.Stage;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelinesTest {

    private static final LocalDate STREAM = LocalDate.parse("2026-11-01");

    private static TimelineDocument document(List<UUID> clips, List<TimelineDocument.Chapter> chapters) {
        return new TimelineDocument("short-9x16", clips, List.of(Map.of("text", "Subscribe")),
                List.of(Map.of("text", "Caption")), chapters);
    }

    private static UUID clip(ContentFixture f, UUID raw, int start) throws Exception {
        return f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, start, start + 10, "Clip " + start, Set.of(), null), "req-" + start).id();
    }

    @Test
    void storedTimelineIsReturnedUnchanged() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        UUID second = clip(f, raw, 100);
        TimelineDocument stored = document(List.of(first, second), List.of(new TimelineDocument.Chapter(0, "Intro")));

        UUID id = f.timelines.store(LibraryFixture.editor(), "Two clips", stored, "req-tl").id();
        ContentViews.TimelineView read = f.timelines.get(LibraryFixture.viewer(), id);

        assertEquals(List.of(first, second), read.document().sourceClipIds(), "the ordered source ranges are kept");
        assertEquals("short-9x16", read.document().layoutTemplate(), "the layout template reference is kept");
        assertEquals(1, read.document().overlays().size(), "the overlays are kept");
        assertEquals(1, read.document().captions().size(), "the captions are kept");
        assertEquals(List.of(new TimelineDocument.Chapter(0, "Intro")), read.document().chapters(), "the chapters are kept");
        assertEquals(LibraryFixture.EDITOR, read.uploaderId(), "the uploader of the timeline is recorded");
    }

    @Test
    void layoutTemplateThatTheBrandKitDoesNotDefineIsRefusedNamingIt() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        TimelineDocument unknown = new TimelineDocument("vertical-9x16", List.of(first), List.of(), List.of(), List.of());

        ProblemException refused = assertThrows(ProblemException.class,
                () -> f.timelines.store(LibraryFixture.editor(), "Unknown layout", unknown, "req-tl"));

        assertEquals(400, refused.status(), "a template the brand kit does not define is a validation error");
        assertTrue(refused.detail().contains("vertical-9x16"), "the problem names the template: " + refused.detail());
    }

    @Test
    void layoutTemplateOfTheBrandKitIsAccepted() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);

        UUID id = f.timelines.store(LibraryFixture.editor(), "Known layout", document(List.of(first), List.of()), "req-tl").id();

        assertEquals("short-9x16", f.timelines.get(LibraryFixture.viewer(), id).document().layoutTemplate(),
                "a template of the brand kit is stored");
    }

    @Test
    void timelineWithoutALayoutTemplateIsAccepted() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        TimelineDocument none = new TimelineDocument(null, List.of(first), List.of(), List.of(), List.of());

        UUID id = f.timelines.store(LibraryFixture.editor(), "No layout", none, "req-tl").id();

        assertNull(f.timelines.get(LibraryFixture.viewer(), id).document().layoutTemplate(),
                "the layout template is optional, as the contract describes it");
    }

    @Test
    void reCuttingAClipKeepsTheLineageEdgesOfItsTimeline() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        UUID timeline = f.timelines.store(LibraryFixture.editor(), "Short", document(List.of(first), List.of()), "req-tl")
                .id();
        Set<UUID> before = f.content.clipsOf(null, timeline);

        clip(f, raw, 12);
        f.library.renameClip(LibraryFixture.editor(), first, "Recut", "req-r");

        assertEquals(before, f.content.clipsOf(null, timeline), "the clips of the timeline are the same after a re-cut");
        assertEquals(Set.of(first), f.content.clipsOf(null, timeline), "the timeline still uses the cut it was made from");
    }

    @Test
    void timelineWithUnknownClipIsRefused() throws Exception {
        ContentFixture f = new ContentFixture();

        ProblemException refused = assertThrows(ProblemException.class, () -> f.timelines.store(LibraryFixture.editor(),
                "Ghost", document(List.of(UUID.fromString("6a7b8c9d-0e1f-4a2b-9c3d-5e6f708192a3")), List.of()), "req-tl"));

        assertEquals(400, refused.status(), "a timeline may only use existing clips");
    }

    @Test
    void chapterMarksNotStartingAtZeroAreRefusedWithTheChapterProblem() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);

        ProblemException refused = assertThrows(ProblemException.class, () -> f.timelines.store(LibraryFixture.editor(),
                "Late chapter", document(List.of(first), List.of(new TimelineDocument.Chapter(5, "Intro"))), "req-tl"));

        assertEquals("Invalid chapter marks", refused.title(), "the chapter rule is named");
    }

    @Test
    void viewerCannotStoreTimeline() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);

        assertEquals(403, assertThrows(ProblemException.class, () -> f.timelines.store(LibraryFixture.viewer(), "No",
                document(List.of(first), List.of()), "req-tl")).status());
    }

    @Test
    void registeredVideoMovesItsClipsToStageRendered() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        UUID second = clip(f, raw, 100);
        UUID timeline = f.timelines.store(LibraryFixture.editor(), "Short",
                document(List.of(first, second), List.of()), "req-tl").id();
        UUID video = f.finishedVideo(LibraryFixture.EDITOR);

        ContentViews.RegisteredAssetView registered = f.timelines.register(LibraryFixture.editor(), video, timeline,
                "Short 9x16", "req-reg");

        assertEquals(timeline, registered.timelineId(), "the asset names its timeline");
        assertEquals(Stage.RENDERED, f.content.findClip(null, first).orElseThrow().stage(), "the first clip is at stage rendered");
        assertEquals(Stage.RENDERED, f.content.findClip(null, second).orElseThrow().stage(),
                "the second clip is at stage rendered");
        assertTrue(f.content.findRegistration(null, video).isPresent(), "the asset is registered in the pool");
    }

    @Test
    void registrationWithMissingTimelineIsRejectedAndNothingIsRegistered() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID video = f.finishedVideo(LibraryFixture.EDITOR);

        ProblemException refused = assertThrows(ProblemException.class, () -> f.timelines.register(LibraryFixture.editor(),
                video, UUID.fromString("7b8c9d0e-1f2a-4b3c-8d4e-6f708192a3b4"), "Ghost", "req-reg"));

        assertEquals(404, refused.status(), "a missing timeline rejects the registration");
        assertTrue(f.content.findRegistration(null, video).isEmpty(), "the asset is not listed");
    }

    @Test
    void registrationOfUnverifiedOrUnknownAssetIsRejected() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        UUID timeline = f.timelines.store(LibraryFixture.editor(), "Short", document(List.of(first), List.of()), "req-tl")
                .id();

        assertEquals(404, assertThrows(ProblemException.class, () -> f.timelines.register(LibraryFixture.editor(),
                UUID.fromString("8c9d0e1f-2a3b-4c4d-9e5f-708192a3b4c5"), timeline, "Ghost", "req-reg")).status(),
                "an asset without a verified upload is not registered");
    }

    @Test
    void rawMaterialCannotBeRegisteredAsFinishedAsset() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        UUID timeline = f.timelines.store(LibraryFixture.editor(), "Short", document(List.of(first), List.of()), "req-tl")
                .id();
        UUID recording = f.rawMaterialAsset(LibraryFixture.EDITOR);

        assertEquals(400, assertThrows(ProblemException.class, () -> f.timelines.register(LibraryFixture.editor(),
                recording, timeline, "Recording", "req-reg")).status(), "only video and image are finished assets");
    }

    @Test
    void clipUsedByTimelineCannotBeDeleted() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID first = clip(f, raw, 10);
        f.timelines.store(LibraryFixture.editor(), "Short", document(List.of(first), List.of()), "req-tl");

        assertEquals(409, assertThrows(ProblemException.class,
                () -> f.library.deleteClip(LibraryFixture.editor(), first, "req-del")).status(),
                "lineage keeps the clip, so a used clip is not deleted");
        assertTrue(f.content.findClip(null, first).isPresent(), "the clip remains");
    }

    @Test
    void lineageOfRawItemListsExactlyItsAssetsTimelinesAndClips() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID stream = f.rawItem(LibraryFixture.EDITOR, "Stream one", STREAM, 3600);
        UUID other = f.rawItem(LibraryFixture.EDITOR, "Stream two", STREAM, 3600);
        for (int i = 0; i < 2; i++) {
            UUID clip = clip(f, stream, 10 + i * 100);
            UUID timeline = f.timelines.store(LibraryFixture.editor(), "Short " + i, document(List.of(clip), List.of()),
                    "req-tl-" + i).id();
            f.timelines.register(LibraryFixture.editor(), f.finishedVideo(LibraryFixture.EDITOR), timeline, "Short " + i,
                    "req-reg-" + i);
        }
        UUID foreignClip = clip(f, other, 10);
        UUID foreignTimeline = f.timelines.store(LibraryFixture.editor(), "Other", document(List.of(foreignClip), List.of()),
                "req-tl-o").id();
        f.timelines.register(LibraryFixture.editor(), f.finishedVideo(LibraryFixture.EDITOR), foreignTimeline, "Other",
                "req-reg-o");

        ContentViews.RawLineageView lineage = f.library.lineage(LibraryFixture.viewer(), stream);

        assertEquals(2, lineage.assets().size(), "exactly the two assets made from this stream are returned");
        assertTrue(lineage.assets().stream().allMatch(asset -> asset.clipIds().size() == 1),
                "each asset names its clip");
    }
}
