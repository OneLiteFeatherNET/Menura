package net.onelitefeather.menura.core.library;

import com.nimbusds.jose.util.JSONArrayUtils;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a WebVTT, SRT or JSON transcript into timed segments. The hub never transcribes: it only reads the file the
 * editing PC uploaded. A malformed cue is refused with a Problem Details response that names its line; a malformed JSON
 * segment is refused with the number of its segment.
 */
public final class TranscriptParser {

    private static final Pattern TIMING = Pattern.compile(
            "^\\s*((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d{3})(?:\\s.*)?$");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private TranscriptParser() {
    }

    /**
     * Parses the file content. A file larger than {@code maxBytes} (UTF-8) is refused with 413 before it is read; a
     * file without any cue is refused with 400.
     */
    public static List<TranscriptSegment> parse(String content, long maxBytes) {
        if (content.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ProblemException(413, "Transcript Too Large", "The transcript is larger than the limit of "
                    + maxBytes + " bytes.");
        }
        if (TranscriptFormat.detect(content) == TranscriptFormat.JSON) {
            return parseJson(content);
        }
        boolean webVtt = TranscriptFormat.detect(content) == TranscriptFormat.WEBVTT;
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        String[] lines = text.split("\\r?\\n", -1);
        List<TranscriptSegment> segments = new ArrayList<>();
        int position = 0;
        int blockNumber = 0;
        while (position < lines.length) {
            if (lines[position].isBlank()) {
                position++;
                continue;
            }
            int end = position;
            while (end < lines.length && !lines[end].isBlank()) {
                end++;
            }
            int first = position;
            position = end;
            blockNumber++;
            if (webVtt && blockNumber == 1 && lines[first].startsWith("WEBVTT")) {
                continue;
            }
            if (webVtt && isNonCueBlock(lines[first])) {
                continue;
            }
            segments.add(cue(lines, first, end, blockNumber));
        }
        if (segments.isEmpty()) {
            throw new ProblemException(400, "Invalid transcript", "The file contains no cue with a timing line.");
        }
        return segments;
    }

    /**
     * Reads a JSON array of segments: {@code {"start": seconds, "end": seconds, "speaker": "...", "text": "..."}}. The
     * speaker is optional; every other field is required, and a segment must end after it starts.
     */
    private static List<TranscriptSegment> parseJson(String content) {
        List<?> cues;
        try {
            cues = JSONArrayUtils.parse(content.startsWith("﻿") ? content.substring(1).stripLeading() : content.stripLeading());
        } catch (ParseException malformed) {
            throw new ProblemException(400, "Invalid transcript", "The JSON transcript is not an array of segments.");
        }
        if (cues.isEmpty()) {
            throw new ProblemException(400, "Invalid transcript", "The file contains no segment.");
        }
        List<TranscriptSegment> segments = new ArrayList<>();
        for (int index = 0; index < cues.size(); index++) {
            segments.add(jsonSegment(cues.get(index), index + 1));
        }
        return segments;
    }

    private static TranscriptSegment jsonSegment(Object value, int number) {
        if (!(value instanceof Map<?, ?> cue)) {
            throw new ProblemException(400, "Invalid transcript", "Segment " + number + " is not an object.");
        }
        if (!(cue.get("start") instanceof Number start) || !(cue.get("end") instanceof Number end)) {
            throw new ProblemException(400, "Invalid transcript", "Segment " + number + " needs a numeric start and end.");
        }
        if (!(cue.get("text") instanceof String text) || text.isBlank()) {
            throw new ProblemException(400, "Invalid transcript", "Segment " + number + " has no text.");
        }
        if (cue.get("speaker") != null && !(cue.get("speaker") instanceof String)) {
            throw new ProblemException(400, "Invalid transcript", "Segment " + number + " names a speaker that is not text.");
        }
        long startMillis = Math.round(start.doubleValue() * 1000);
        long endMillis = Math.round(end.doubleValue() * 1000);
        if (startMillis < 0 || endMillis <= startMillis) {
            throw new ProblemException(400, "Invalid transcript", "Segment " + number + " must end after it starts.");
        }
        return new TranscriptSegment(startMillis, endMillis, (String) cue.get("speaker"), text.trim());
    }

    /** NOTE, STYLE and REGION blocks of WebVTT carry no speech. */
    private static boolean isNonCueBlock(String firstLine) {
        return firstLine.equals("NOTE") || firstLine.startsWith("NOTE ") || firstLine.equals("STYLE")
                || firstLine.equals("REGION") || firstLine.startsWith("STYLE ") || firstLine.startsWith("REGION ");
    }

    private static TranscriptSegment cue(String[] lines, int first, int end, int cueNumber) {
        int timingLine = first;
        if (!lines[first].contains("-->") && end - first >= 2) {
            timingLine = first + 1;
        }
        Matcher timing = TIMING.matcher(lines[timingLine]);
        if (!timing.matches()) {
            throw new ProblemException(400, "Invalid transcript", "At line " + (timingLine + 1) + ": the timing line of cue "
                    + cueNumber + " cannot be parsed.");
        }
        long start = millis(timing.group(1));
        long stop = millis(timing.group(2));
        if (stop <= start) {
            throw new ProblemException(400, "Invalid transcript", "At line " + (timingLine + 1) + ": cue " + cueNumber
                    + " ends before it starts.");
        }
        StringBuilder text = new StringBuilder();
        for (int line = timingLine + 1; line < end; line++) {
            String cleaned = TAG.matcher(lines[line]).replaceAll("").trim();
            if (!cleaned.isEmpty()) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(cleaned);
            }
        }
        if (text.isEmpty()) {
            throw new ProblemException(400, "Invalid transcript", "At line " + (timingLine + 1) + ": cue " + cueNumber
                    + " has no text.");
        }
        return new TranscriptSegment(start, stop, null, text.toString());
    }

    /** Reads hh:mm:ss.mmm, mm:ss.mmm, with a comma or a dot before the milliseconds. */
    static long millis(String timestamp) {
        String[] parts = timestamp.split("[.,]");
        String[] clock = parts[0].split(":");
        long hours = clock.length == 3 ? Long.parseLong(clock[0]) : 0;
        long minutes = Long.parseLong(clock[clock.length - 2]);
        long seconds = Long.parseLong(clock[clock.length - 1]);
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + Long.parseLong(parts[1]);
    }
}
