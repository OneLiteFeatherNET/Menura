package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptParserTest {

    private static final long LIMIT = 10L * 1024 * 1024;

    private static final String WEBVTT = """
            WEBVTT

            NOTE the editing PC wrote this file

            1
            00:00:01.000 --> 00:00:03.500
            Hello <v streamer>chat</v>

            00:01:02.250 --> 00:01:05.000 line:90%
            Second cue

            00:10:00.000 --> 00:10:02.000
            Third cue
            """;

    private static final String SRT = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello chat

            2
            01:02:03,004 --> 01:02:05,000
            Second cue
            on two lines
            """;

    @Test
    void webVttCuesParseIntoSegmentsWithStartEndAndText() {
        List<TranscriptSegment> segments = TranscriptParser.parse(WEBVTT, LIMIT);

        assertEquals(3, segments.size(), "three cues give three segments");
        assertEquals(1_000, segments.get(0).startMillis(), "the first cue starts at one second");
        assertEquals(3_500, segments.get(0).endMillis(), "the first cue ends at 3.5 seconds");
        assertEquals("Hello chat", segments.get(0).text(), "voice tags are removed from the text");
        assertEquals(62_250, segments.get(1).startMillis(), "mm:ss.mmm timings are read");
        assertEquals("Third cue", segments.get(2).text(), "the last cue is kept");
    }

    @Test
    void webVttSegmentsCarryNoSpeaker() {
        List<TranscriptSegment> segments = TranscriptParser.parse(WEBVTT, LIMIT);

        assertNull(segments.get(0).speaker(), "WebVTT has no speaker field");
    }

    @Test
    void srtCuesParseIntoSegments() {
        List<TranscriptSegment> segments = TranscriptParser.parse(SRT, LIMIT);

        assertEquals(2, segments.size(), "two cues give two segments");
        assertEquals(3_723_004, segments.get(1).startMillis(), "hh:mm:ss,mmm timings are read with the comma");
        assertEquals("Second cue on two lines", segments.get(1).text(), "multi-line text is joined with a space");
    }

    @Test
    void malformedTimingLineIsRefusedWithItsLineNumber() {
        String broken = "1\n00:00:01,000 --> 00:00:03,500\nFine\n\n2\n00:00:05,000 -> 00:00:06,000\nBroken\n";

        ProblemException refused = assertThrows(ProblemException.class, () -> TranscriptParser.parse(broken, LIMIT));

        assertEquals(400, refused.status(), "a malformed file is a client error");
        assertTrue(refused.detail().contains("line 6"), "the problem names the failing line: " + refused.detail());
    }

    @Test
    void transcriptAboveTheSizeLimitIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> TranscriptParser.parse(WEBVTT, 10));

        assertEquals(413, refused.status(), "an oversized transcript is refused as too large");
    }

    @Test
    void fileWithoutAnyCueIsRefused() {
        assertThrows(ProblemException.class, () -> TranscriptParser.parse("WEBVTT\n", LIMIT),
                "a transcript without any cue is refused");
    }

    private static final String JSON = """
            [
              {"start": 1.0, "end": 3.5, "speaker": "Alice", "text": "Hello chat"},
              {"start": 62.25, "end": 65, "speaker": "Bob", "text": "Second cue"}
            ]
            """;

    @Test
    void jsonSegmentsStoreTheSpeakerOfEachSegment() {
        List<TranscriptSegment> segments = TranscriptParser.parse(JSON, LIMIT);

        assertEquals("Alice", segments.get(0).speaker(), "the speaker of the first segment is kept");
        assertEquals("Bob", segments.get(1).speaker(), "the speaker of the second segment is kept");
    }

    @Test
    void jsonSegmentsParseSecondsIntoMilliseconds() {
        List<TranscriptSegment> segments = TranscriptParser.parse(JSON, LIMIT);

        assertEquals(1_000, segments.get(0).startMillis(), "a start of 1.0 seconds is 1000 milliseconds");
        assertEquals(3_500, segments.get(0).endMillis(), "an end of 3.5 seconds is 3500 milliseconds");
        assertEquals(62_250, segments.get(1).startMillis(), "fractional starts are kept to the millisecond");
        assertEquals("Second cue", segments.get(1).text(), "the text of the segment is kept");
    }

    @Test
    void jsonSegmentWithoutSpeakerHasNoSpeaker() {
        List<TranscriptSegment> segments = TranscriptParser.parse(
                "[{\"start\": 1, \"end\": 2, \"text\": \"Alone\"}]", LIMIT);

        assertNull(segments.get(0).speaker(), "a segment without a speaker field has none");
    }

    @Test
    void jsonSegmentThatEndsBeforeItStartsIsRefusedNamingItsPosition() {
        ProblemException refused = assertThrows(ProblemException.class, () -> TranscriptParser.parse(
                "[{\"start\": 1, \"end\": 2, \"text\": \"Fine\"}, {\"start\": 5, \"end\": 4, \"text\": \"Broken\"}]",
                LIMIT));

        assertEquals(400, refused.status(), "a malformed JSON transcript is a client error");
        assertTrue(refused.detail().contains("Segment 2"), "the problem names the failing segment: " + refused.detail());
    }

    @Test
    void jsonFormatIsDetectedFromTheArray() {
        assertEquals(TranscriptFormat.JSON, TranscriptFormat.detect(JSON), "a JSON array selects the JSON format");
    }

    @Test
    void formatIsDetectedFromTheHeader() {
        assertEquals(TranscriptFormat.WEBVTT, TranscriptFormat.detect(WEBVTT), "a WEBVTT header selects WebVTT");
        assertEquals(TranscriptFormat.SRT, TranscriptFormat.detect(SRT), "anything else is read as SRT");
    }
}
