package net.onelitefeather.menura.core.analytics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishSlotTest {

    @Test
    void aPostLateOnSundayUtcCountsAsMondayInBerlin() {
        Instant publishedAt = Instant.parse("2026-11-08T23:30:00Z");

        assertEquals(1, PublishSlot.weekday(publishedAt), "00:30 on Monday in Europe/Berlin is a Monday");
        assertEquals(0, PublishSlot.hour(publishedAt), "the publish hour is the hour in Europe/Berlin");
    }

    @Test
    void theHourIsTheHourOfTheBerlinClock() {
        Instant publishedAt = Instant.parse("2026-11-09T17:00:00Z");

        assertEquals(18, PublishSlot.hour(publishedAt), "17:00 UTC is 18:00 in Europe/Berlin in November");
    }

    @Test
    void theWeekStartsOnTheMondayOfTheBerlinWeek() {
        Instant publishedAt = Instant.parse("2026-11-09T17:00:00Z");

        assertEquals(LocalDate.parse("2026-11-09"), PublishSlot.weekStart(publishedAt), "a Monday starts its own week");
        assertEquals("2026-W46", PublishSlot.isoWeek(publishedAt), "the ISO week follows the Berlin date");
    }

    @Test
    void aSundayBelongsToTheWeekThatStartedSixDaysBefore() {
        Instant publishedAt = Instant.parse("2026-11-15T12:00:00Z");

        assertEquals(LocalDate.parse("2026-11-09"), PublishSlot.weekStart(publishedAt), "Sunday ends the Monday week");
    }
}
