package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsoWeekTest {

    @Test
    void thursdayOfTheFortyFifthWeekIsInWeekFortyFive() {
        assertEquals("2026-W45", IsoWeek.of(Instant.parse("2026-11-05T17:00:00Z")));
    }

    @Test
    void lastSecondOfSundayStillBelongsToWeekFortyFour() {
        assertEquals("2026-W44", IsoWeek.of(Instant.parse("2026-11-01T23:59:59Z")));
    }

    @Test
    void newYearsDayCanBelongToTheLastWeekOfThePreviousYear() {
        assertEquals("2026-W53", IsoWeek.of(Instant.parse("2027-01-01T12:00:00Z")));
    }

    @Test
    void wellFormedWeekAcceptsTheContractShapeOnly() {
        assertTrue(IsoWeek.isWellFormed("2026-W45"));
        assertFalse(IsoWeek.isWellFormed("2026-45"));
        assertFalse(IsoWeek.isWellFormed("2026-W54"));
    }
}
