package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seeded first version and its exports: the overlay theme roles, the feather mark and no colour outside the kit. */
class InitialBrandKitTest {

    private static final Pattern HEX_IN_CSS = Pattern.compile("#[0-9a-fA-F]{6}");

    @Test
    void theColourRolesAreTheOverlayThemeRolesForLightAndDark() {
        BrandKit kit = InitialBrandKit.create();

        assertEquals(new ColourToken("primary", "#6638e4", "#ccbdff"), kit.colour("primary").orElseThrow(),
                "primary is the light and dark role of the overlay theme");
        assertEquals(new ColourToken("surface", "#fffbff", "#1c1b1e"), kit.colour("surface").orElseThrow(),
                "surface is the light and dark role of the overlay theme");
    }

    @Test
    void theMarkIsTheFeatherWithALightAndADarkVariantEachWithASafeArea() {
        BrandKit.Mark feather = InitialBrandKit.create().marks().get(0);

        assertEquals("feather", feather.name());
        assertEquals(2, feather.variants().size(), "a light and a dark variant");
        feather.variants().forEach(variant -> assertTrue(variant.safeArea() != null, "variant " + variant.name()
                + " declares a safe area"));
    }

    @Test
    void thePublishedSeedRampCarriesTheTonesOfItsSeedColour() {
        BrandKit prepared = BrandKitRules.prepare(InitialBrandKit.create());

        assertEquals(TonalPalette.derive(InitialBrandKit.SEED), prepared.ramps().get(0).tones());
    }

    @Test
    void theCssExportListsEveryColourTokenWithItsLightAndDarkValues() {
        String css = BrandKitCss.of(BrandKitRules.prepare(InitialBrandKit.create()));

        assertTrue(css.contains("  --brand-primary: #6638e4;"), "the light value of primary");
        assertTrue(css.contains("    --brand-primary: #ccbdff;"), "the dark value of primary under the media query");
        assertTrue(css.contains(":root[data-theme=\"dark\"] {\n  --brand-primary: #ccbdff;"),
                "the dark value under the explicit dark theme");
    }

    @Test
    void theCssExportContainsNoColourOutsideTheKit() {
        BrandKit kit = BrandKitRules.prepare(InitialBrandKit.create());
        Set<String> allowed = new HashSet<>();
        kit.colours().forEach(token -> {
            allowed.add(token.light());
            allowed.add(token.dark());
        });
        kit.ramps().forEach(ramp -> ramp.tones().forEach(tone -> allowed.add(tone.hex())));

        Matcher colours = HEX_IN_CSS.matcher(BrandKitCss.of(kit));
        while (colours.find()) {
            assertTrue(allowed.contains(colours.group().toLowerCase(java.util.Locale.ROOT)),
                    "the export holds a colour outside the kit: " + colours.group());
        }
    }
}
