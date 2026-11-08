package net.onelitefeather.menura.core.analytics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishSlotTest {

    private static final PublishSlot BERLIN = new PublishSlot(ZoneId.of("Europe/Berlin"));

    @Test
    void aPostLateOnSundayUtcCountsAsMondayInBerlin() {
        Instant publishedAt = Instant.parse("2026-11-08T23:30:00Z");

        assertEquals(1, BERLIN.weekday(publishedAt), "00:30 on Monday in Europe/Berlin is a Monday");
        assertEquals(0, BERLIN.hour(publishedAt), "the publish hour is the hour in Europe/Berlin");
    }

    @Test
    void theHourIsTheHourOfTheBerlinClock() {
        Instant publishedAt = Instant.parse("2026-11-09T17:00:00Z");

        assertEquals(18, BERLIN.hour(publishedAt), "17:00 UTC is 18:00 in Europe/Berlin in November");
    }

    @Test
    void theWeekStartsOnTheMondayOfTheBerlinWeek() {
        Instant publishedAt = Instant.parse("2026-11-09T17:00:00Z");

        assertEquals(LocalDate.parse("2026-11-09"), BERLIN.weekStart(publishedAt), "a Monday starts its own week");
        assertEquals("2026-W46", BERLIN.isoWeek(publishedAt), "the ISO week follows the Berlin date");
    }

    @Test
    void aSundayBelongsToTheWeekThatStartedSixDaysBefore() {
        Instant publishedAt = Instant.parse("2026-11-15T12:00:00Z");

        assertEquals(LocalDate.parse("2026-11-09"), BERLIN.weekStart(publishedAt), "Sunday ends the Monday week");
    }

    @Test
    void theZoneOfTheSlotIsTheConfiguredZoneNotAFixedOne() {
        PublishSlot utc = new PublishSlot(ZoneId.of("UTC"));
        Instant publishedAt = Instant.parse("2026-11-08T23:30:00Z");

        assertEquals(23, utc.hour(publishedAt), "with UTC as the zone the hour is the UTC hour");
        assertEquals(7, utc.weekday(publishedAt), "with UTC as the zone the same instant is a Sunday");
    }
}
