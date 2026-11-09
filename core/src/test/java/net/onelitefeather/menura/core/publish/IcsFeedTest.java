package net.onelitefeather.menura.core.publish;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feed against the structural rules of RFC 5545: CRLF line ends, a line of at most 75 octets, balanced components,
 * the required properties of every event, and escaped text. The last test hands the whole feed to the RFC 5545 validator of
 * ical4j, a test-scope dependency.
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

    @Test
    void theFeedPassesTheRfc5545ValidatorOfIcal4j() throws Exception {
        String feed = IcsFeed.render(NOW, List.of(
                event("post-7", "Stream clip, part 1; a\\b", NOW.plusSeconds(3600)),
                new IcsFeed.Event("post-8", "Clip", "Line one\nLine two", NOW, 2, NOW)));

        Calendar calendar = new CalendarBuilder().build(new StringReader(feed));
        ValidationResult result = calendar.validate(true);

        assertFalse(result.hasErrors(), "the feed has no RFC 5545 violation: " + result);
        assertEquals(2, calendar.getComponents(Component.VEVENT).size(), "both events are read back");
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
