package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.Tone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TonalPaletteTest {

    @Test
    void theSameSeedAlwaysYieldsIdenticalTones() {
        List<Tone> first = TonalPalette.derive("#6a3de8");
        List<Tone> second = TonalPalette.derive("#6a3de8");

        assertEquals(first, second, "the derivation is a pure function of the seed");
    }

    @Test
    void aNewSeedUpdatesTheDerivedTones() {
        assertNotEquals(TonalPalette.derive("#6a3de8"), TonalPalette.derive("#e83d6a"),
                "a new seed must yield different tones");
    }

    @Test
    void theRampCoversTheBlackToWhiteSteps() {
        List<Tone> tones = TonalPalette.derive("#6a3de8");

        assertEquals(TonalPalette.STEPS.length, tones.size(), "one tone per step");
        assertEquals("#000000", tones.get(0).hex(), "tone 0 is black");
        assertEquals("#ffffff", tones.get(tones.size() - 1).hex(), "tone 100 is white");
    }

    @Test
    void everyToneIsAColourValue() {
        for (Tone tone : TonalPalette.derive("#6a3de8")) {
            assertTrue(Colour.isHex(tone.hex()), "tone " + tone.tone() + " must be a colour value: " + tone.hex());
        }
    }
}
