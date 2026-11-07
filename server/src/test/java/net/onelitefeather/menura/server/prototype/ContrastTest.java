package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContrastTest {

    @Test
    void whiteOnBlackIsTwentyOneToOne() {
        assertEquals(21.0, Contrast.ratio("#FFFFFF", "#000000"), 1e-9);
    }

    @Test
    void identicalColoursAreOneToOne() {
        assertEquals(1.0, Contrast.ratio("#6638E4", "#6638E4"), 1e-9);
    }

    @Test
    void theRatioDoesNotDependOnTheOrderOfTheColours() {
        assertEquals(Contrast.ratio("#FFFFFF", "#2F6FEB"), Contrast.ratio("#2F6FEB", "#FFFFFF"), 1e-12);
    }

    @Test
    void everySeedSeriesPairMeetsTheMinimumRatio() {
        for (Model.Series series : DemoSeed.series()) {
            assertTrue(Contrast.ratio(series.badgeTextColor(), series.color()) >= Contrast.MINIMUM,
                    series.id() + " must meet 4.5:1");
        }
    }

    @Test
    void onlySixDigitHexColoursAreAccepted() {
        assertTrue(Contrast.isHex("#2F6FEB"));
        assertFalse(Contrast.isHex("#FFF"));
        assertFalse(Contrast.isHex("2F6FEB"));
    }
}
