package net.onelitefeather.menura.core.publish;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the read-only iCalendar feed of one person (RFC 5545). Only standard properties are written, so every calendar
 * client reads the feed without an extension. Text is escaped and lines are folded at 75 octets, on character boundaries.
 */
public final class IcsFeed {

    /** One event. {@code sequence} is the revision marker: it grows whenever the underlying post changes. */
    public record Event(String uid, String summary, String description, Instant start, int sequence, Instant lastModified) {
    }

    static final Duration LENGTH = Duration.ofMinutes(30);
    static final String PRODUCT = "-//OneLiteFeather//Menura//EN";
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int OCTETS = 75;

    private IcsFeed() {
    }

    /** The whole feed as CRLF-terminated lines. The events keep the order given. */
    public static String render(Instant now, List<Event> events) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:" + PRODUCT);
        line(out, "CALSCALE:GREGORIAN");
        for (Event event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + escape(event.uid()));
            line(out, "DTSTAMP:" + UTC.format(now));
            line(out, "DTSTART:" + UTC.format(event.start()));
            line(out, "DTEND:" + UTC.format(event.start().plus(LENGTH)));
            line(out, "SUMMARY:" + escape(event.summary()));
            line(out, "DESCRIPTION:" + escape(event.description()));
            line(out, "SEQUENCE:" + event.sequence());
            line(out, "LAST-MODIFIED:" + UTC.format(event.lastModified()));
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** Escapes a text value: backslash, semicolon, comma and newline, as RFC 5545 section 3.3.11 requires. */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Writes one content line, folded so that no physical line exceeds 75 octets. */
    static void line(StringBuilder out, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        int start = 0;
        boolean first = true;
        while (start < bytes.length || first) {
            int limit = first ? OCTETS : OCTETS - 1;
            int end = Math.min(bytes.length, start + limit);
            while (end < bytes.length && end > start && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            if (!first) {
                out.append(' ');
            }
            out.append(new String(bytes, start, end - start, StandardCharsets.UTF_8)).append("\r\n");
            start = end;
            first = false;
        }
    }
}
