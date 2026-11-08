package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RawMediaTest {

    private static final LocalDate STREAM = LocalDate.parse("2026-11-01");

    private static RawMedia media(ContentFixture f) {
        return new RawMedia(f.base.transactions, f.content, f.base.storage, f.base.auditLog);
    }

    @Test
    void explicitRemovalMarksMediaRemovedAndLineageStillNamesTheRawItem() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        UUID clip = f.library.createClip(LibraryFixture.editor(),
                new ContentLibrary.ClipRequest(raw, 10, 20, "Cut", Set.of(), null), "req-1").id();
        UUID timeline = f.timelines.store(LibraryFixture.editor(), "Short",
                new TimelineDocument(null, List.of(clip), List.of(), List.of(), List.of()), "req-2").id();
        f.timelines.register(LibraryFixture.editor(), f.finishedVideo(LibraryFixture.EDITOR), timeline, "Short", "req-3");

        media(f).removeMedia(LibraryFixture.editor(), raw, "req-4");

        assertEquals("removed", f.content.findRawItem(null, raw).orElseThrow().mediaState(),
                "the raw item keeps its row and says that its media is removed");
        assertEquals(1, f.library.lineage(LibraryFixture.viewer(), raw).assets().size(),
                "lineage still names the finished asset made from the removed recording");
    }

    @Test
    void removalWritesExactlyOneAuditEntry() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        int before = f.base.audit.entries().size();

        media(f).removeMedia(LibraryFixture.editor(), raw, "req-4");

        assertEquals(1, f.base.audit.entries().size() - before, "the deletion writes one audit entry");
        assertEquals("raw_item.media_delete", f.base.audit.entries().get(before).action(), "the entry names the deletion");
    }

    @Test
    void viewerCannotRemoveRawMedia() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        assertEquals(403, assertThrows(ProblemException.class,
                () -> media(f).removeMedia(LibraryFixture.viewer(), raw, "req-4")).status());
        assertEquals("available", f.content.findRawItem(null, raw).orElseThrow().mediaState(), "the media is kept");
    }

    @Test
    void removingAlreadyRemovedMediaIsRefused() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        media(f).removeMedia(LibraryFixture.editor(), raw, "req-4");

        assertEquals(409, assertThrows(ProblemException.class,
                () -> media(f).removeMedia(LibraryFixture.editor(), raw, "req-5")).status());
    }
}
