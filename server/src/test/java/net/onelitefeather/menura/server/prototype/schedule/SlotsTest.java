package net.onelitefeather.menura.server.prototype.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure slot arithmetic: no clock, no store, no server. */
class SlotsTest {

    private static final LocalTime SIX_PM = LocalTime.of(18, 0);

    @Test
    void theNextSlotsSkipAnInstantThatIsTaken() {
        Instant taken = Instant.parse("2026-11-12T17:00:00Z");

        List<Instant> slots = Slots.next(DayOfWeek.THURSDAY, SIX_PM, Instant.parse("2026-11-08T12:00:00Z"), 2,
                at -> at.equals(taken));

        assertEquals(List.of(Instant.parse("2026-11-19T17:00:00Z"), Instant.parse("2026-11-26T17:00:00Z")), slots,
                "the taken Thursday is skipped and the next two Thursdays follow");
    }

    @Test
    void aSlotKeepsItsBerlinWallTimeAcrossTheEndOfSummerTime() {
        // Summer time ends on Sunday 25 Oct 2026: 18:00 Berlin is 16:00 UTC before it and 17:00 UTC after it.
        List<Instant> slots = Slots.next(DayOfWeek.TUESDAY, SIX_PM, Instant.parse("2026-10-19T12:00:00Z"), 2,
                at -> false);

        assertEquals(List.of(Instant.parse("2026-10-20T16:00:00Z"), Instant.parse("2026-10-27T17:00:00Z")), slots,
                "18:00 Berlin keeps its wall time; only the UTC instant moves");
    }

    @Test
    void futureSlotsAfterTheEndOfSummerTimeAreAtSeventeenUtc() {
        List<Instant> slots = Slots.next(DayOfWeek.TUESDAY, SIX_PM, Instant.parse("2026-11-08T12:00:00Z"), 1,
                at -> false);

        assertEquals(Instant.parse("2026-11-10T17:00:00Z"), slots.get(0),
                "the next Tuesday 18:00 Berlin is in winter time, so 17:00 UTC");
    }

    @Test
    void aSlotExactlyAtTheEarliestInstantIsStillOffered() {
        Instant earliest = Instant.parse("2026-11-10T17:00:00Z");

        List<Instant> slots = Slots.next(DayOfWeek.TUESDAY, SIX_PM, earliest, 1, at -> false);

        assertEquals(earliest, slots.get(0), "the earliest instant is inclusive");
    }

    @Test
    void aSlotOneSecondBeforeTheEarliestInstantIsSkipped() {
        Instant earliest = Instant.parse("2026-11-10T17:00:01Z");

        List<Instant> slots = Slots.next(DayOfWeek.TUESDAY, SIX_PM, earliest, 1, at -> false);

        assertEquals(Instant.parse("2026-11-17T17:00:00Z"), slots.get(0), "the slot before the bound is not offered");
    }

    @Test
    void theNumberOfSlotsIsExactlyTheCountAsked() {
        List<Instant> slots = Slots.next(DayOfWeek.FRIDAY, SIX_PM, Instant.parse("2026-11-08T12:00:00Z"), 4,
                at -> false);

        assertEquals(4, slots.size(), "four slots were asked for");
    }
}
