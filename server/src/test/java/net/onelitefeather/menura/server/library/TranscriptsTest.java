package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptsTest {

    private static final LocalDate STREAM = LocalDate.parse("2026-11-01");
    private static final String THREE_CUES = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\none\n\n"
            + "00:00:03.000 --> 00:00:04.000\ntwo\n\n00:00:05.000 --> 00:00:06.000\nthree\n";
    private static final String TWO_CUES = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nfour\n\n"
            + "00:00:03.000 --> 00:00:04.000\nfive\n";

    @Test
    void webVttUploadStoresOneSegmentPerCue() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ContentViews.TranscriptView view = f.transcripts.upload(LibraryFixture.editor(), raw, THREE_CUES, "req-t");

        assertEquals(3, f.content.segmentCount(raw), "three cues give three stored segments");
        assertEquals("webvtt", view.format(), "the format is the one detected in the file");
        assertEquals(3, view.segmentCount(), "the response reports the stored segment count");
        assertEquals(false, view.replaced(), "a first transcript replaces nothing");
    }

    @Test
    void jsonUploadStoresTheSpeakerOfEachSegment() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        String json = "[{\"start\": 1, \"end\": 2, \"speaker\": \"Alice\", \"text\": \"Speaker words\"}]";

        ContentViews.TranscriptView view = f.transcripts.upload(LibraryFixture.editor(), raw, json, "req-j");
        List<ContentRepository.SearchHit> hits = f.content.search(null, "speaker", ContentRepository.PoolFilter.NONE, 10);

        assertEquals("json", view.format(), "the JSON file is stored in the JSON format");
        assertEquals("Alice", hits.get(0).segment().speaker(), "the stored segment keeps its speaker");
    }

    @Test
    void webVttUploadStoresNoSpeaker() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        f.transcripts.upload(LibraryFixture.editor(), raw, THREE_CUES, "req-v");
        List<ContentRepository.SearchHit> hits = f.content.search(null, "two", ContentRepository.PoolFilter.NONE, 10);

        assertEquals(null, hits.get(0).segment().speaker(), "a WebVTT segment stores no speaker");
    }

    @Test
    void replacementReplacesAllSegmentsAndWritesOneAuditEntry() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        f.transcripts.upload(LibraryFixture.editor(), raw, THREE_CUES, "req-1");
        int before = f.base.audit.entries().size();

        ContentViews.TranscriptView view = f.transcripts.upload(LibraryFixture.editor(), raw, TWO_CUES, "req-2");

        assertEquals(2, f.content.segmentCount(raw), "only the two new segments remain");
        assertEquals(true, view.replaced(), "the response says that a transcript was replaced");
        assertEquals(1, f.base.audit.entries().size() - before, "the replacement writes one audit entry");
        assertEquals("transcript.replace", f.base.audit.entries().get(before).action(), "the entry names the replacement");
    }

    @Test
    void malformedTranscriptStoresNothingAndWritesNoAuditEntry() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        int before = f.base.audit.entries().size();

        ProblemException refused = assertThrows(ProblemException.class, () -> f.transcripts.upload(LibraryFixture.editor(),
                raw, "WEBVTT\n\nbroken cue\n", "req-t"));

        assertEquals(400, refused.status(), "a file that cannot be parsed is refused");
        assertEquals(0, f.content.segmentCount(raw), "no segment is stored");
        assertEquals(before, f.base.audit.entries().size(), "a refused upload writes no audit entry");
    }

    @Test
    void transcriptAboveTheConfiguredLimitIsRefused() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);
        Transcripts small = new Transcripts(f.base.transactions, f.base.clock, f.content, f.base.auditLog, 20);

        ProblemException refused = assertThrows(ProblemException.class,
                () -> small.upload(LibraryFixture.editor(), raw, THREE_CUES, "req-t"));

        assertEquals(413, refused.status(), "a transcript above the limit is refused");
        assertEquals(0, f.content.segmentCount(raw), "nothing is stored");
    }

    @Test
    void viewerCannotUploadTranscript() throws Exception {
        ContentFixture f = new ContentFixture();
        UUID raw = f.rawItem(LibraryFixture.EDITOR, "Coop night", STREAM, 3600);

        ProblemException refused = assertThrows(ProblemException.class,
                () -> f.transcripts.upload(LibraryFixture.viewer(), raw, THREE_CUES, "req-t"));

        assertEquals(403, refused.status(), "only editors attach transcripts");
        assertTrue(f.content.transcriptFormat(raw) == null, "no transcript is recorded");
    }

    @Test
    void transcriptForUnknownRawItemIsNotFound() throws Exception {
        ContentFixture f = new ContentFixture();

        ProblemException refused = assertThrows(ProblemException.class, () -> f.transcripts.upload(LibraryFixture.editor(),
                UUID.fromString("5f6a7b8c-9d0e-4f1a-8b2c-4d5e6f708192"), THREE_CUES, "req-t"));

        assertEquals(404, refused.status(), "a transcript needs an existing raw item");
    }
}
