package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.PartPlan;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.Channel;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static net.onelitefeather.menura.server.library.LibraryFixture.GIB;
import static net.onelitefeather.menura.server.library.LibraryFixture.MIB;
import static net.onelitefeather.menura.server.library.LibraryFixture.checksumFor;
import static net.onelitefeather.menura.server.library.LibraryFixture.compositeOf;
import static net.onelitefeather.menura.server.library.LibraryFixture.editor;
import static net.onelitefeather.menura.server.library.LibraryFixture.filled;
import static net.onelitefeather.menura.server.library.LibraryFixture.otherEditor;
import static net.onelitefeather.menura.server.library.LibraryFixture.rawRecording;
import static net.onelitefeather.menura.server.library.LibraryFixture.video;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetUploadsTest {

    private static final long PART = 95 * MIB;

    @Test
    void startCreatesOneMultipartUploadAndAPresignedUrlForEveryPart() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;

        UploadSessionView view = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        assertEquals(1, f.storage.multipartUploadsCreated(), "starting an upload creates exactly one multipart upload");
        assertEquals(view.partCount(), view.parts().size(), "every part of a new upload gets a presigned URL");
        assertTrue(view.parts().get(0).url().startsWith("https://s3.test/"), "the URL points at the storage endpoint");
    }

    @Test
    void startMovesTheSessionToUploading() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;

        UploadSessionView view = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        assertEquals("uploading", view.state(), "a new upload is in state uploading");
    }

    @Test
    void partsOfATwelveGigabyteVideoAreAtMostNinetyFiveMegabytes() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 12 * GIB;

        UploadSessionView view = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        assertTrue(view.parts().size() > 1, "a 12 GB file needs several parts");
        for (PartUrl part : view.parts()) {
            assertTrue(part.sizeBytes() <= PartPlan.MAX_PART_SIZE,
                    "part " + part.partNumber() + " must be at most 95 MB but is " + part.sizeBytes() + " bytes");
        }
    }

    @Test
    void rawRecordingIsRecordedAsRawMaterialWithItsStreamTitleAndDate() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 40 * GIB;

        UploadSessionView view = f.uploads.start(editor(), rawRecording(size, checksumFor(size, PART)), "req-1");

        assertEquals("raw_material", view.kind(), "a raw recording is stored as raw material");
        assertEquals("Coop night", view.streamTitle());
        assertEquals("2026-11-01", view.streamDate().toString());
        for (PartUrl part : view.parts()) {
            assertTrue(part.sizeBytes() <= PartPlan.MAX_PART_SIZE, "raw parts must be at most 95 MB too");
        }
    }

    @Test
    void rawRecordingAboveTheLimitIsRefusedWithProblemDetailsAndCreatesNoSession() {
        LibraryFixture f = new LibraryFixture();
        long size = 60 * GIB;

        ProblemException refused = assertThrows(ProblemException.class, () ->
                f.uploads.start(editor(), rawRecording(size, checksumFor(size, PART)), "req-1"));

        assertEquals(413, refused.status());
        assertTrue(refused.detail().contains(String.valueOf(50 * GIB)), "the refusal names the limit: " + refused.detail());
        assertTrue(f.sessions.rows().isEmpty(), "a refused raw recording creates no session");
        assertEquals(0, f.storage.multipartUploadsCreated(), "a refused raw recording starts no storage upload");
    }

    @Test
    void checksumWithAnotherPartCountIsRefusedBeforeAnyStorageCall() {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;

        ProblemException refused = assertThrows(ProblemException.class, () ->
                f.uploads.start(editor(), video(size, "A".repeat(43) + "=-2"), "req-1"));

        assertEquals(400, refused.status());
        assertEquals(0, f.storage.multipartUploadsCreated(), "a checksum of the wrong part count starts nothing");
    }

    @Test
    void resumeReportsTheConfirmedPartsAndContinuesWithTheFirstUnconfirmedOne() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 79 * PART + 1;
        UploadSessionView started = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");
        String uploadId = f.sessions.find(null, started.id()).orElseThrow().storageUploadId();
        for (int part = 1; part <= 40; part++) {
            f.storage.receivePart(uploadId, part, PART, FakeObjectStorage.digestOf(filled(4, part)));
        }

        UploadSessionView resumed = f.uploads.read(editor(), started.id());

        assertEquals(80, started.partCount(), "the file is 80 parts");
        assertEquals(40, resumed.confirmedParts().size(), "parts 1 to 40 are reported as confirmed");
        assertEquals(41, resumed.nextPart(), "the upload continues with part 41");
        assertEquals(41, resumed.parts().get(0).partNumber(), "the first URL issued is for part 41");
        assertEquals(40, resumed.parts().size(), "only the missing parts get URLs");
    }

    @Test
    void reloadReturnsTheSameSessionAndCreatesNoSecondOne() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;
        UploadSessionView started = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        UploadSessionView reloaded = f.uploads.read(editor(), started.id());

        assertEquals(started.id(), reloaded.id(), "the reload returns the same session");
        assertEquals(1, f.sessions.rows().size(), "no second session is created by a reload");
        assertEquals(1, f.storage.multipartUploadsCreated(), "no second storage upload is created by a reload");
    }

    @Test
    void expiredPartUrlIsRefusedAndTheResumeIssuesFreshUrls() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;
        UploadSessionView started = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");
        String staleUrl = started.parts().get(0).url();
        f.clock.advance(Duration.ofMinutes(16));

        assertFalse(f.storage.accepts(staleUrl), "a part URL older than its lifetime is refused by the storage");
        UploadSessionView resumed = f.uploads.read(editor(), started.id());
        assertTrue(f.storage.accepts(resumed.parts().get(0).url()), "the resume issues a URL that is still valid");
    }

    @Test
    void completeWithMatchingChecksumRecordsAndListsTheAsset() throws SQLException {
        LibraryFixture f = new LibraryFixture(5 * MIB);
        List<byte[]> parts = List.of(filled((int) (5 * MIB), 1), filled((int) MIB, 2));
        UploadSessionView started = f.uploads.start(editor(), video(6 * MIB, compositeOf(parts)), "req-1");
        f.sendAllParts(started.id(), parts);

        UploadSessionView done = f.uploads.complete(editor(), started.id(), "req-2");

        assertEquals("available", done.state(), "a matching checksum makes the upload available");
        assertNotNull(done.assetId(), "the completed upload names its asset");
        assertEquals(1, f.assets.rows().size(), "the asset is listed");
    }

    @Test
    void completeWithMismatchedChecksumFailsTheSessionListsNothingAndRemovesTheBytes() throws SQLException {
        LibraryFixture f = new LibraryFixture(5 * MIB);
        List<byte[]> declared = List.of(filled((int) (5 * MIB), 1), filled((int) MIB, 2));
        List<byte[]> sent = List.of(filled((int) (5 * MIB), 9), filled((int) MIB, 2));
        UploadSessionView started = f.uploads.start(editor(), video(6 * MIB, compositeOf(declared)), "req-1");
        f.sendAllParts(started.id(), sent);

        ProblemException refused = assertThrows(ProblemException.class, () ->
                f.uploads.complete(editor(), started.id(), "req-2"));

        assertEquals(422, refused.status(), "the editor sees the mismatch as an unprocessable upload");
        assertEquals("failed", f.sessions.find(null, started.id()).orElseThrow().state().value());
        assertTrue(f.assets.rows().isEmpty(), "a mismatched upload is never listed");
        assertFalse(f.storage.hasObject(UploadSession.keyFor(started.id())), "the bytes of the wrong file are removed");
    }

    @Test
    void completeWithAMissingPartIsRefusedNamingThePart() throws SQLException {
        LibraryFixture f = new LibraryFixture(5 * MIB);
        List<byte[]> parts = List.of(filled((int) (5 * MIB), 1), filled((int) MIB, 2));
        UploadSessionView started = f.uploads.start(editor(), video(6 * MIB, compositeOf(parts)), "req-1");
        f.storage.receivePart(f.sessions.find(null, started.id()).orElseThrow().storageUploadId(), 1, 5 * MIB,
                FakeObjectStorage.digestOf(parts.get(0)));

        ProblemException refused = assertThrows(ProblemException.class, () ->
                f.uploads.complete(editor(), started.id(), "req-2"));

        assertEquals(409, refused.status());
        assertTrue(refused.detail().contains("Part 2"), "the refusal names the missing part: " + refused.detail());
    }

    @Test
    void duplicateChecksumFlagsTheUploadWithAReferenceAndStartsNoStorageUpload() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        List<byte[]> bytes = List.of(filled(10, 3));
        UUID existing = f.uploadAsset(editor(), video(10, compositeOf(bytes)), bytes);

        UploadSessionView flagged = f.uploads.start(editor(), video(10, compositeOf(bytes)), "req-3");

        assertEquals("flagged", flagged.state(), "a known checksum flags the upload");
        assertEquals(existing, flagged.duplicateOf(), "the flag names the existing asset");
        assertTrue(flagged.parts().isEmpty(), "a flagged upload gets no part URLs until it is confirmed");
        assertEquals(1, f.storage.multipartUploadsCreated(), "the flagged upload starts no storage upload");
    }

    @Test
    void confirmedDuplicateContinuesAndCompletionCreatesASecondRecord() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        List<byte[]> bytes = List.of(filled(10, 3));
        f.uploadAsset(editor(), video(10, compositeOf(bytes)), bytes);
        UploadSessionView flagged = f.uploads.start(editor(), video(10, compositeOf(bytes)), "req-3");

        UploadSessionView confirmed = f.uploads.confirmDuplicate(editor(), flagged.id(), "req-4");
        f.sendAllParts(flagged.id(), bytes);
        f.uploads.complete(editor(), flagged.id(), "req-5");

        assertEquals("uploading", confirmed.state(), "a confirmed duplicate continues as an upload");
        assertEquals(2, f.assets.rows().size(), "the editor's confirmation creates a second record with the same checksum");
    }

    @Test
    void checksumKnownOnlyToAnotherUploaderIsNotFlagged() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        List<byte[]> bytes = List.of(filled(10, 3));
        f.uploadAsset(editor(), video(10, compositeOf(bytes)), bytes);

        UploadSessionView other = f.uploads.start(otherEditor(), video(10, compositeOf(bytes)), "req-3");

        assertEquals("uploading", other.state(), "duplicate detection compares within one uploader only");
    }

    @Test
    void cancelAbortsTheStorageUploadAndMarksTheSessionCancelled() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;
        UploadSessionView started = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        UploadSessionView cancelled = f.uploads.cancel(editor(), started.id(), "req-2");

        assertEquals("cancelled", cancelled.state());
        assertEquals(0, f.storage.openUploads(), "the storage upload is aborted");
    }

    @Test
    void anotherEditorCannotSeeTheSessionAndGetsNotFound() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;
        UploadSessionView started = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        ProblemException hidden = assertThrows(ProblemException.class, () -> f.uploads.read(otherEditor(), started.id()));

        assertEquals(404, hidden.status(), "a session of another uploader answers 404, not 403");
    }

    @Test
    void startWritesOneAuditEntryWithActorActionTargetChannelAndRequest() throws SQLException {
        LibraryFixture f = new LibraryFixture();
        long size = 3 * GIB;

        UploadSessionView started = f.uploads.start(editor(), video(size, checksumFor(size, PART)), "req-1");

        assertEquals(1, f.audit.entries().size(), "starting an upload is one audited write");
        AuditEntry entry = f.audit.entries().get(0);
        assertEquals("asset_upload.start", entry.action());
        assertEquals("upload_session:" + started.id(), entry.target());
        assertEquals(LibraryFixture.EDITOR, entry.personId());
        assertEquals(Channel.WEB, entry.channel());
        assertEquals("req-1", entry.requestId());
    }
}
