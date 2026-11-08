package net.onelitefeather.menura.core.analytics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffsetsTest {

    @Test
    void anHourOldPostHasOnlyTheOneHourOffsetDue() {
        List<Duration> due = Offsets.due(Duration.ofMinutes(65), Set.of());

        assertEquals(List.of(Duration.ofHours(1)), due, "only the +1 hour offset has been reached");
    }

    @Test
    void anOffsetAlreadyTakenIsNotDueAgain() {
        List<Duration> due = Offsets.due(Duration.ofHours(3), Set.of(Duration.ofHours(1).toMinutes()));

        assertEquals(List.of(Duration.ofHours(2)), due, "the +1 hour snapshot exists and only +2 hours is due");
    }

    @Test
    void dailyOffsetsStartOnTheEighthDay() {
        Set<Long> fixed = Set.of(Duration.ofHours(1).toMinutes(), Duration.ofHours(2).toMinutes(),
                Duration.ofHours(4).toMinutes(), Duration.ofHours(8).toMinutes(), Duration.ofHours(24).toMinutes(),
                Duration.ofHours(48).toMinutes(), Duration.ofDays(7).toMinutes());

        List<Duration> due = Offsets.due(Duration.ofDays(9), fixed);

        assertEquals(List.of(Duration.ofDays(8), Duration.ofDays(9)), due,
                "the snapshots of day eight and day nine are due after the fixed ones are taken");
    }

    @Test
    void aWholeDayOffsetFromTheEighthDayIsAllowedForTheComparison() {
        assertTrue(Offsets.allowed(Duration.ofDays(8)), "day eight is a daily offset");
        assertTrue(Offsets.allowed(Duration.ofHours(24)), "+24 hours is a fixed offset");
    }

    @Test
    void anOffsetThatIsNotOnTheScheduleIsRefused() {
        assertFalse(Offsets.allowed(Duration.ofHours(5)), "+5 hours is not a snapshot offset");
        assertFalse(Offsets.allowed(Duration.ofHours(72)), "+72 hours is day three, before the daily snapshots start");
        assertFalse(Offsets.allowed(Duration.ofDays(Offsets.MAX_DAY + 1)), "a day past the daily bound is refused");
    }
}
