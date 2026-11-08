package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.Timing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingTest {

    private static final double THIRTY_SECONDS = 30;

    @Test
    void aTimingFromZeroToThreeSecondsIsVisibleBeforeThreeSecondsOnly() {
        Timing title = new Timing(0, 3.0, false);

        assertTrue(title.visibleAt(2.5, THIRTY_SECONDS), "visible at 2.5 seconds");
        assertFalse(title.visibleAt(3.5, THIRTY_SECONDS), "not visible at 3.5 seconds");
    }

    @Test
    void aTimingAnchoredToTheEndCountsBackFromTheEnd() {
        Timing lastTwoSeconds = new Timing(2, null, true);

        assertFalse(lastTwoSeconds.visibleAt(20, THIRTY_SECONDS), "not visible at 20 seconds");
        assertTrue(lastTwoSeconds.visibleAt(28.5, THIRTY_SECONDS), "visible at 28.5 seconds");
    }

    @Test
    void aTimingWithoutAnEndLastsToTheEndOfTheOutput() {
        Timing badge = new Timing(0, null, false);

        assertTrue(badge.visibleAt(0, THIRTY_SECONDS), "visible at the start");
        assertTrue(badge.visibleAt(29, THIRTY_SECONDS), "visible at 29 seconds");
    }
}
