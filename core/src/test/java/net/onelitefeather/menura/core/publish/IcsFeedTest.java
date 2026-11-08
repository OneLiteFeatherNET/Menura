package net.onelitefeather.menura.core.publish;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feed against the structural rules of RFC 5545: CRLF line ends, a line of at most 75 octets, balanced components,
 * the required properties of every event, and escaped text. No external validator is on the build path, so the rules
 * the RFC states are checked here.
 */
class IcsFeedTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void theFeedUsesCrlfLinesAndBalancedComponents() {
        String feed = IcsFeed.render(NOW, List.of(event("post-1", "Stream clip", NOW.plusSeconds(3600))));

        assertTrue(feed.endsWith("\r\n"), "every line ends with CRLF");
        assertFalse(feed.replace("\r\n", "").contains("\n"), "no bare line feed is written");
        assertEquals(List.of("BEGIN:VCALENDAR", "BEGIN:VEVENT", "END:VEVENT", "END:VCALENDAR"),
                components(feed), "the components open and close in order");
    }

    @Test
    void everyPhysicalLineIsAtMostSeventyFiveOctets() {
        String title = "Ä".repeat(60);
        String feed = IcsFeed.render(NOW, List.of(event("post-2", title, NOW)));

        for (String line : feed.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75,
                    "a line is folded at 75 octets: " + line);
        }
    }

    @Test
    void foldedTextRejoinsToTheOriginalValue() {
        String title = "Ä".repeat(60);
        String feed = IcsFeed.render(NOW, List.of(event("post-3", title, NOW)));

        String unfolded = feed.replace("\r\n ", "");
        assertTrue(unfolded.contains("SUMMARY:" + title), "a folded summary rejoins to the original text");
    }

    @Test
    void everyEventHasItsRequiredProperties() {
        String feed = IcsFeed.render(NOW, List.of(event("post-4", "Clip", NOW.plusSeconds(60))));

        for (String property : List.of("UID:", "DTSTAMP:", "DTSTART:", "SUMMARY:")) {
            assertTrue(feed.contains("\r\n" + property), property + " is written for the event");
        }
        assertTrue(feed.contains("DTSTART:20261102T100100Z"), "the start is written in UTC");
    }

    @Test
    void commasSemicolonsBackslashesAndNewlinesAreEscaped() {
        String feed = IcsFeed.render(NOW, List.of(event("post-5", "a,b;c\\d\ne", NOW)));

        assertTrue(feed.contains("SUMMARY:a\\,b\\;c\\\\d\\ne"), "the text is escaped as RFC 5545 requires");
    }

    @Test
    void theRevisionMarkerIsWrittenAsSequence() {
        IcsFeed.Event event = new IcsFeed.Event("post-6", "Clip", "", NOW, 4, NOW);

        String feed = IcsFeed.render(NOW, List.of(event));

        assertTrue(feed.contains("SEQUENCE:4"), "the revision marker is the sequence number");
    }

    private static IcsFeed.Event event(String uid, String summary, Instant start) {
        return new IcsFeed.Event(uid, summary, "Channel", start, 0, NOW);
    }

    private static List<String> components(String feed) {
        List<String> out = new ArrayList<>();
        for (String line : feed.split("\r\n")) {
            if (line.startsWith("BEGIN:") || line.startsWith("END:")) {
                out.add(line);
            }
        }
        return out;
    }
}
