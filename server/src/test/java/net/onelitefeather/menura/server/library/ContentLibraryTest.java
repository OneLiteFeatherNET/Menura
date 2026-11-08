package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.onelitefeather.menura.server.library.ContentFixture.PERSON_B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentLibraryTest {

    private static final LocalDate STREAM = LocalDate.parse("2026-11-01");

    @Test
    void editorCreatesClipWithRangeTitleTagsAndStageCut() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ContentViews.ClipView clip = f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 120, 180, "Boss fight", Set.of("speedrun"), null), "req-clip");

        assertEquals("cut", clip.stage(), "a new clip is at stage cut");
        assertEquals(120, clip.startSeconds(), "the clip keeps its start");
        assertEquals(LibraryFixture.EDITOR, clip.uploaderId(), "the creator is the uploader");
        assertEquals(Set.of("speedrun"), Set.copyOf(clip.freeTags()), "the free tags are stored on the clip");
    }

    @Test
    void clipEndingAfterItsRecordingIsRefusedAndStoresNothing() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ProblemException refused = assertThrows(ProblemException.class, () -> f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 3500, 3700, "Too long", Set.of(), null), "req-clip"));

        assertEquals(400, refused.status(), "a clip outside its recording is a validation error");
        assertTrue(f.content.clips().isEmpty(), "no clip is stored");
    }

    @Test
    void viewerCannotCreateClip() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ProblemException refused = assertThrows(ProblemException.class, () -> f.library.createClip(LibraryFixture.viewer(),
                new ContentLibrary.ClipRequest(raw, 10, 20, "Viewer clip", Set.of(), null), "req-clip"));

        assertEquals(403, refused.status(), "a viewer may not add clips to the pool");
        assertTrue(f.content.clips().isEmpty(), "no clip is stored for the viewer");
    }

    @Test
    void uploaderIsKeptWhenAnotherEditorRetitlesTheClip() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID clip = f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 10, 20, "First title", Set.of(), null), "req-1").id();

        ContentViews.ClipView renamed = f.library.renameClip(LibraryFixture.otherEditor(), clip, "New title", "req-2");

        assertEquals("New title", renamed.title(), "the editor changed the title");
        assertEquals(LibraryFixture.EDITOR, renamed.uploaderId(), "the uploader is still the first member");
    }

    @Test
    void viewerWhoWasEditorCannotRenameClip() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID clip = f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 10, 20, "Keep me", Set.of(), null), "req-1").id();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> f.library.renameClip(LibraryFixture.viewer(), clip, "Changed", "req-2"));

        assertEquals(403, refused.status(), "editing follows the role, not the uploader");
        assertEquals("Keep me", f.content.findClip(null, clip).orElseThrow().title(), "the title is unchanged");
    }

    @Test
    void invalidFreeTagIsRefusedAndNothingIsStored() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ProblemException refused = assertThrows(ProblemException.class, () -> f.library.setTags(LibraryFixture.editor(),
                raw, Set.of("Speed Run"), Set.of(), "req-tags"));

        assertEquals(400, refused.status(), "a tag with an upper-case letter and a space is a validation error");
        assertTrue(f.content.tagsOf(null, List.of(raw)).get(raw).freeTags().isEmpty(), "nothing is stored");
    }

    @Test
    void editorAddsPersonTagAndTheRawItemListsThatPerson() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ContentViews.ItemTagsView tags = f.library.setTags(LibraryFixture.editor(), raw, Set.of(), Set.of(PERSON_B),
                "req-tags");

        assertEquals(List.of(PERSON_B), tags.personTags(), "the person who appears in the recording is tagged");
    }

    @Test
    void viewerCannotChangeTags() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ProblemException refused = assertThrows(ProblemException.class, () -> f.library.setTags(LibraryFixture.viewer(),
                raw, Set.of("speedrun"), Set.of(), "req-tags"));

        assertEquals(403, refused.status(), "a viewer may not tag items");
    }

    @Test
    void poolListsRawItemAndClipWithKindAndStage() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        f.library.createClip(LibraryFixture.otherEditor(), new ContentLibrary.ClipRequest(raw, 60, 90, "Clutch", Set.of(), null),
                "req-clip");

        ContentViews.PoolPageView page = f.library.pool(LibraryFixture.viewer(), ContentLibrary.PoolQuery.NONE, null, 50);

        assertEquals(2, page.items().size(), "a raw item and a clip are both listed");
        assertEquals(List.of("clip", "raw"), page.items().stream().map(ContentViews.PoolItemView::kind).sorted().toList(),
                "the items carry their kind");
        assertEquals(List.of("cut", "raw"), page.items().stream().map(ContentViews.PoolItemView::stage).sorted().toList(),
                "the items carry their stage");
    }

    @Test
    void poolFilterMineReturnsOnlyTheCallersItems() throws Exception {
        ContentFixture f = new ContentFixture();
        f.rawItem(LibraryFixture.EDITOR, "Mine", STREAM, 3600);
        f.rawItem(LibraryFixture.OTHER_EDITOR, "Theirs", STREAM, 3600);

        ContentViews.PoolPageView page = f.library.pool(LibraryFixture.editor(),
                new ContentLibrary.PoolQuery(true, null, null, null, null, null, null, null), null, 50);

        assertEquals(List.of("Mine"), page.items().stream().map(ContentViews.PoolItemView::title).toList(),
                "only the caller's items are returned");
    }

    @Test
    void combinedFiltersApplyWithAnd() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID first = f.rawItem(LibraryFixture.EDITOR, "Tagged raw", STREAM, 3600);
        f.library.setTags(LibraryFixture.editor(), first, Set.of(), Set.of(PERSON_B), "req-1");
        UUID untagged = f.rawItem(LibraryFixture.EDITOR, "Untagged raw", STREAM, 3600);
        f.library.createClip(LibraryFixture.editor(), new ContentLibrary.ClipRequest(untagged, 1, 5, "Cut", Set.of(), null),
                "req-2");

        ContentViews.PoolPageView page = f.library.pool(LibraryFixture.viewer(),
                new ContentLibrary.PoolQuery(false, LibraryFixture.EDITOR, PERSON_B, null, "raw", null, null, null), null, 50);

        assertEquals(List.of("Tagged raw"), page.items().stream().map(ContentViews.PoolItemView::title).toList(),
                "uploader, person and stage must all match");
    }

    @Test
    void suggestedClipIsStoredAndListedAsSuggested() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 300, 330, "Highlight", Set.of(), "suggested"), "req-s");

        ContentViews.PoolPageView page = f.library.pool(LibraryFixture.viewer(),
                new ContentLibrary.PoolQuery(false, null, null, null, null, null, null, null), null, 50);
        assertEquals(List.of("suggested"), page.items().stream().filter(item -> "clip".equals(item.kind()))
                .map(ContentViews.PoolItemView::status).toList(), "the clip is listed with status suggested");
    }

    @Test
    void deletedClipIsGoneAndOneAuditEntryRecordsTheDeletion() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID clip = f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 10, 20, "Gone soon", Set.of(), null), "req-1").id();
        int before = f.base.audit.entries().size();

        f.library.deleteClip(LibraryFixture.editor(), clip, "req-del");

        assertTrue(f.content.findClip(null, clip).isEmpty(), "the clip is removed");
        assertEquals(1, f.base.audit.entries().size() - before, "the deletion writes exactly one audit entry");
        assertEquals("clip.delete", f.base.audit.entries().get(f.base.audit.entries().size() - 1).action(),
                "the entry names the deletion");
    }

    @Test
    void viewerCannotDeleteClip() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID clip = f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 10, 20, "Keep", Set.of(), null), "req-1").id();

        assertEquals(403, assertThrows(ProblemException.class,
                () -> f.library.deleteClip(LibraryFixture.viewer(), clip, "req-del")).status());
        assertFalse(f.content.findClip(null, clip).isEmpty(), "the clip is kept");
    }

    @Test
    void searchReturnsTheSegmentWithItsRawItemAndStartTime() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        f.transcripts.upload(LibraryFixture.editor(), raw, "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello chat\n\n"
                + "00:01:02.000 --> 00:01:04.000\nWe play the speedrun now\n", "req-t");

        List<ContentViews.SearchHitView> hits = f.library.search(LibraryFixture.viewer(), "speedrun",
                ContentLibrary.PoolQuery.NONE, 20);

        assertEquals(1, hits.size(), "one segment contains the word");
        assertEquals(raw, hits.get(0).rawItemId(), "the hit names its raw item");
        assertEquals(62_000, hits.get(0).startMillis(), "the hit opens at the start of the speech");
    }

    @Test
    void searchWithoutMatchReturnsNothing() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        f.transcripts.upload(LibraryFixture.editor(), raw, "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello chat\n", "req-t");

        assertTrue(f.library.search(LibraryFixture.viewer(), "dragon", ContentLibrary.PoolQuery.NONE, 20).isEmpty(),
                "a word that is not spoken matches nothing");
    }

    @Test
    void recordingWithoutTranscriptHasNoSearchHits() throws Exception {
        ContentFixture f = new ContentFixture();
        f.rawItem(LibraryFixture.EDITOR, "Silent recording", STREAM, 3600);

        assertTrue(f.library.search(LibraryFixture.viewer(), "anything", ContentLibrary.PoolQuery.NONE, 20).isEmpty(),
                "no transcript means no segment");
    }

    @Test
    void stageFilterKeepsOnlyHitsOfRawItemsWithAClipAtThatStage() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID withClip = f.rawItem(LibraryFixture.EDITOR, "With clip", STREAM, 3600);
        UUID without = f.rawItem(LibraryFixture.EDITOR, "Without clip", STREAM, 3600);
        String vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nspeedrun\n";
        f.transcripts.upload(LibraryFixture.editor(), withClip, vtt, "req-1");
        f.transcripts.upload(LibraryFixture.editor(), without, vtt, "req-2");
        f.library.createClip(LibraryFixture.editor(), new ContentLibrary.ClipRequest(withClip, 1, 5, "Cut", Set.of(), null),
                "req-3");

        List<ContentViews.SearchHitView> hits = f.library.search(LibraryFixture.viewer(), "speedrun",
                new ContentLibrary.PoolQuery(false, null, null, null, "cut", null, null, null), 20);

        assertEquals(List.of(withClip), hits.stream().map(ContentViews.SearchHitView::rawItemId).toList(),
                "only the raw item with a cut clip remains");
    }

    @Test
    void searchFilteredByUploaderOnlyReturnsThatUploadersRecordings() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID mine = f.rawItem(LibraryFixture.EDITOR, "Mine", STREAM, 3600);
        UUID theirs = f.rawItem(LibraryFixture.OTHER_EDITOR, "Theirs", STREAM, 3600);
        String vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nspeedrun\n";
        f.transcripts.upload(LibraryFixture.editor(), mine, vtt, "req-1");
        f.transcripts.upload(LibraryFixture.otherEditor(), theirs, vtt, "req-2");

        List<ContentViews.SearchHitView> hits = f.library.search(LibraryFixture.viewer(), "speedrun",
                new ContentLibrary.PoolQuery(false, LibraryFixture.OTHER_EDITOR, null, null, null, null, null, null), 20);

        assertEquals(List.of(theirs), hits.stream().map(ContentViews.SearchHitView::rawItemId).toList(),
                "the uploader filter restricts the hits");
        assertEquals(LibraryFixture.OTHER_EDITOR, hits.get(0).uploaderId(), "the hit names its uploader");
    }
}
