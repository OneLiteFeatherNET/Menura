package net.onelitefeather.menura.core.brand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColourTest {

    @Test
    void blackOnWhiteHasTheMaximumContrastRatio() {
        assertEquals(21.0, Colour.contrast("#000000", "#ffffff"), 1e-9, "WCAG maximum is 21:1");
    }

    @Test
    void contrastDoesNotDependOnTheOrderOfTheColours() {
        assertEquals(Colour.contrast("#6638e4", "#ffffff"), Colour.contrast("#ffffff", "#6638e4"), 1e-12,
                "the ratio is symmetric");
    }

    @Test
    void aHexValueIsAcceptedOnlyInTheSixDigitForm() {
        assertTrue(Colour.isHex("#6A3DE8"), "an upper-case six digit value is a colour");
        assertFalse(Colour.isHex("#fff"), "the three digit form is not accepted");
        assertFalse(Colour.isHex("6A3DE8"), "a value without the hash is not a colour");
        assertFalse(Colour.isHex(null), "a missing value is not a colour");
    }

    @Test
    void aColourIsNormalisedToLowerCase() {
        assertEquals("#6a3de8", Colour.normalise("#6A3DE8"), "the stored form is lower case");
    }
}
