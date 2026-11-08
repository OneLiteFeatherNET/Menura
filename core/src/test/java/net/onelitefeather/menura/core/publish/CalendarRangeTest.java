package net.onelitefeather.menura.core.publish;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalendarRangeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void aWeekRunsFromMondayToSunday() {
        CalendarRange range = CalendarRange.of(CalendarRange.View.WEEK, LocalDate.of(2026, 11, 11), 0, BERLIN);

        assertEquals(LocalDate.of(2026, 11, 9), range.firstDay(), "the week starts on the Monday before the date");
        assertEquals(LocalDate.of(2026, 11, 15), range.lastDay(), "the week ends on the Sunday after it");
        assertEquals(Instant.parse("2026-11-08T23:00:00Z"), range.from(), "the week starts at local midnight in UTC");
    }

    @Test
    void aMonthCoversItsWholeLocalDays() {
        CalendarRange range = CalendarRange.of(CalendarRange.View.MONTH, LocalDate.of(2026, 11, 8), 0, BERLIN);

        assertEquals(LocalDate.of(2026, 11, 1), range.firstDay(), "the month starts on its first day");
        assertEquals(LocalDate.of(2026, 11, 30), range.lastDay(), "the month ends on its last day");
        assertEquals(Instant.parse("2026-11-30T23:00:00Z"), range.to(), "the end is exclusive, at local midnight");
    }

    @Test
    void aListCoversTheRequestedDays() {
        CalendarRange range = CalendarRange.of(CalendarRange.View.LIST, LocalDate.of(2026, 11, 8), 7, BERLIN);

        assertEquals(LocalDate.of(2026, 11, 14), range.lastDay(), "seven days starting on the date");
    }

    @Test
    void aListOfMoreThanNinetyTwoDaysIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> CalendarRange.of(CalendarRange.View.LIST, LocalDate.of(2026, 11, 8), 93, BERLIN),
                "a list is bounded");
    }
}
