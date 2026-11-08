package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.brand.BrandKit.Slot;
import net.onelitefeather.menura.core.brand.BrandKit.Template;
import net.onelitefeather.menura.core.brand.BrandKit.Timing;
import net.onelitefeather.menura.core.brand.BrandKit.TypographyRole;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The publication rules of a brand kit, each on its own behaviour, starting from the valid first kit. */
class BrandKitRulesTest {

    private static final String ASSET = "3f1c2a4e-5b6d-4c7e-9f8a-0b1c2d3e4f50";

    @Test
    void theInitialKitIsPublishable() {
        assertEquals(List.of(), BrandKitRules.violations(InitialBrandKit.create()),
                "the seeded first version must pass every rule");
    }

    @Test
    void aColourTokenWithoutItsDarkRoleIsRefusedAndNamed() {
        BrandKit kit = replaceToken(InitialBrandKit.create(), "surface", new ColourToken("surface", "#fffbff", null));

        assertTrue(violations(kit).contains("colour token 'surface' lacks the dark role"), violations(kit).toString());
    }

    @Test
    void aTextRoleBelowAaContrastIsRefusedWithThePairAndRatio() {
        BrandKit kit = replaceToken(InitialBrandKit.create(), "on-surface", new ColourToken("on-surface", "#e0e0e0", "#e6e1e6"));

        String violation = violations(kit).stream().filter(text -> text.contains("on-surface on surface")).findFirst()
                .orElseThrow(() -> new AssertionError("no contrast violation in " + violations(kit)));
        assertTrue(violation.contains("in the light role"), violation);
        assertTrue(violation.contains("contrast 1."), "the ratio is named: " + violation);
        assertTrue(violation.contains("4.50:1"), "the AA threshold is named: " + violation);
    }

    @Test
    void aTextRoleThatPassesAaInBothRolesIsAccepted() {
        assertEquals(List.of(), BrandKitRules.violations(InitialBrandKit.create()),
                "on-surface on surface meets AA in both roles");
    }

    @Test
    void aTypographyRoleWithoutAFontAssetIsRefusedAndNamed() {
        BrandKit base = InitialBrandKit.create();
        BrandKit kit = withTypography(base, List.of(new TypographyRole("headline", "", List.of(64, 48))));

        assertTrue(violations(kit).contains("typography role 'headline' has no font asset"), violations(kit).toString());
    }

    @Test
    void aTypographyRoleWithoutASizeSetIsRefused() {
        BrandKit kit = withTypography(InitialBrandKit.create(),
                List.of(new TypographyRole("body", ASSET, List.of())));

        assertTrue(violations(kit).contains("typography role 'body' has no positive size set"), violations(kit).toString());
    }

    @Test
    void aTypographyRoleWithAVerifiableFontAssetAndSizesIsAccepted() {
        BrandKit kit = withTypography(InitialBrandKit.create(),
                List.of(new TypographyRole("body", ASSET, List.of(32, 24))));

        assertEquals(List.of(), violations(kit), "a font asset id with a size set is publishable");
    }

    @Test
    void aMarkVariantWithoutAnAssetIsRefusedAndNamed() {
        BrandKit base = InitialBrandKit.create();
        BrandKit kit = new BrandKit(base.colours(), base.ramps(), base.textRoles(), base.typography(),
                List.of(new BrandKit.Mark("feather", List.of(new BrandKit.MarkVariant("light", "marks/light.svg",
                        new BrandKit.SafeArea(10, 10, 10, 10))))), base.voice(), base.texts(), base.templates());

        assertTrue(violations(kit).contains("mark 'feather' variant 'light' has no asset"), violations(kit).toString());
    }

    @Test
    void theReferencedAssetsAreTheFontsAndTheMarkFiles() {
        BrandKit base = InitialBrandKit.create();
        UUID mark = UUID.fromString("9b2d1f0e-3c4a-4e5f-8a6b-7c8d9e0f1a2b");
        BrandKit kit = new BrandKit(base.colours(), base.ramps(), base.textRoles(),
                List.of(new TypographyRole("body", ASSET, List.of(32))),
                List.of(new BrandKit.Mark("feather", List.of(new BrandKit.MarkVariant("light", mark.toString(),
                        new BrandKit.SafeArea(10, 10, 10, 10))))), base.voice(), base.texts(), base.templates());

        assertEquals(Set.of(UUID.fromString(ASSET), mark), BrandKitRules.referencedAssets(kit),
                "the server checks exactly these assets against the library");
    }

    @Test
    void aTemplateWithARawColourNamesTheElementHoldingIt() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "title", slot -> withColour(slot, "#ff0000"));

        assertTrue(violations(kit).contains("slot 'title' of template 'short-9x16' holds the raw colour value '#ff0000', "
                + "use a colour token"), violations(kit).toString());
    }

    @Test
    void aTemplateReferringToAnUnknownTokenNamesTheToken() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "captions", slot -> withColour(slot, "accent-2"));

        assertTrue(violations(kit).contains("slot 'captions' of template 'short-9x16' refers to the unknown colour token "
                + "'accent-2'"), violations(kit).toString());
    }

    @Test
    void aSlotUsingAnUndeclaredParameterIsRefused() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "captions", slot -> withParameter(slot, "subtitle"));
        BrandKit withoutDeclaration = replaceTemplate(kit, "short-9x16", template -> new Template(template.format(),
                template.name(), template.width(), template.height(), template.slots(),
                template.parameters().stream().filter(p -> !p.name().equals("subtitle")).toList()));

        assertTrue(violations(withoutDeclaration).contains("slot 'captions' of template 'short-9x16' uses the undeclared "
                + "parameter 'subtitle'"), violations(withoutDeclaration).toString());
    }

    @Test
    void theTitleMayOnlyBeShownFromZeroToThreeSeconds() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "title", slot -> withTiming(slot, new Timing(0, 4.0, false)));

        assertTrue(violations(kit).contains("title must be shown only from 0 to 3 seconds"), violations(kit).toString());
    }

    @Test
    void theCallToActionMayOnlyBeShownInTheLastTwoSeconds() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "cta", slot -> withTiming(slot, new Timing(5, null, true)));

        assertTrue(violations(kit).contains("cta must be shown only during the last 2 seconds"), violations(kit).toString());
    }

    @Test
    void theShortRequiresTheJoinDomainParameterForTheCallToAction() {
        BrandKit kit = replaceTemplate(InitialBrandKit.create(), "short-9x16", template -> new Template(template.format(),
                template.name(), template.width(), template.height(), template.slots(),
                template.parameters().stream().filter(p -> !p.name().equals("join_domain")).toList()));

        assertTrue(violations(kit).contains("the cta requires the required text parameter join_domain"), violations(kit).toString());
    }

    @Test
    void theShortMayNotContainAnEndCard() {
        BrandKit kit = replaceTemplate(InitialBrandKit.create(), "short-9x16", template -> withExtraSlot(template,
                new Slot("end-card", "shape", null, "surface", null, 0, 0, 100, 100, new Timing(0, null, false))));

        assertTrue(violations(kit).contains("the short template contains an end card"), violations(kit).toString());
    }

    @Test
    void aBadgeInTheTopFourteenPercentIsRefusedAndNamed() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "badge", slot -> withBox(slot, 5));

        assertTrue(violations(kit).contains("slot 'badge' of template 'short-9x16' lies in the top 14 % or bottom 35 % "
                + "of the frame"), violations(kit).toString());
    }

    @Test
    void aTitleStartingInTheBottomThirtyFivePercentIsRefusedAndNamed() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "title", slot -> withBox(slot, 70));

        assertTrue(violations(kit).stream().anyMatch(v -> v.startsWith("slot 'title' of template 'short-9x16' lies in")),
                violations(kit).toString());
    }

    @Test
    void aCaptionsSlotInsideTheSafeZoneIsAccepted() {
        BrandKit kit = replaceSlot(InitialBrandKit.create(), "short-9x16", "captions", slot -> withBox(slot, 14));

        assertEquals(List.of(), violations(kit), "captions from 14 % to 65 % are inside the safe zone");
    }

    @Test
    void aFormatWithoutItsTemplateIsRefused() {
        BrandKit base = InitialBrandKit.create();
        BrandKit kit = new BrandKit(base.colours(), base.ramps(), base.textRoles(), base.typography(), base.marks(),
                base.voice(), base.texts(), base.templates().stream().filter(t -> !t.format().equals("thumbnail")).toList());

        assertTrue(violations(kit).contains("no template for format 'thumbnail'"), violations(kit).toString());
    }

    @Test
    void aTemplateOfTheWrongSizeIsRefused() {
        BrandKit kit = replaceTemplate(InitialBrandKit.create(), "short-9x16", template -> new Template(template.format(),
                template.name(), 1000, 1920, template.slots(), template.parameters()));

        assertTrue(violations(kit).contains("template 'short-9x16' must be 1080 by 1920 pixels"), violations(kit).toString());
    }

    @Test
    void prepareRefusesADraftWithAViolationAsUnprocessableWithProblemDetails() {
        BrandKit kit = replaceToken(InitialBrandKit.create(), "surface", new ColourToken("surface", "#fffbff", null));

        ProblemException refused = assertThrows(ProblemException.class, () -> BrandKitRules.prepare(kit));

        assertEquals(422, refused.status());
        assertTrue(refused.detail().contains("colour token 'surface' lacks the dark role"), refused.detail());
    }

    @Test
    void prepareDerivesTheTonesOfEveryRampFromItsSeed() {
        BrandKit prepared = assertDoesNotThrow(() -> BrandKitRules.prepare(InitialBrandKit.create()));

        assertEquals(TonalPalette.derive(InitialBrandKit.SEED), prepared.ramps().get(0).tones(),
                "the stored tones are the tones of the seed");
    }

    private static List<String> violations(BrandKit kit) {
        return new ArrayList<>(BrandKitRules.violations(kit));
    }

    static BrandKit replaceToken(BrandKit base, String name, ColourToken replacement) {
        List<ColourToken> colours = base.colours().stream()
                .map(token -> token.name().equals(name) ? replacement : token).toList();
        return new BrandKit(colours, base.ramps(), base.textRoles(), base.typography(), base.marks(), base.voice(),
                base.texts(), base.templates());
    }

    static BrandKit withTypography(BrandKit base, List<TypographyRole> typography) {
        return new BrandKit(base.colours(), base.ramps(), base.textRoles(), typography, base.marks(), base.voice(),
                base.texts(), base.templates());
    }

    static BrandKit replaceTemplate(BrandKit base, String format, java.util.function.UnaryOperator<Template> change) {
        List<Template> templates = base.templates().stream()
                .map(template -> template.format().equals(format) ? change.apply(template) : template).toList();
        return new BrandKit(base.colours(), base.ramps(), base.textRoles(), base.typography(), base.marks(), base.voice(),
                base.texts(), templates);
    }

    static BrandKit replaceSlot(BrandKit base, String format, String slotName, java.util.function.UnaryOperator<Slot> change) {
        return replaceTemplate(base, format, template -> new Template(template.format(), template.name(), template.width(),
                template.height(), template.slots().stream()
                        .map(slot -> slot.name().equals(slotName) ? change.apply(slot) : slot).toList(),
                template.parameters()));
    }

    private static Template withExtraSlot(Template template, Slot extra) {
        List<Slot> slots = new ArrayList<>(template.slots());
        slots.add(extra);
        return new Template(template.format(), template.name(), template.width(), template.height(), slots, template.parameters());
    }

    private static Slot withColour(Slot slot, String colour) {
        return new Slot(slot.name(), slot.kind(), colour, slot.background(), slot.parameter(), slot.x(), slot.y(),
                slot.width(), slot.height(), slot.timing());
    }

    private static Slot withParameter(Slot slot, String parameter) {
        return new Slot(slot.name(), slot.kind(), slot.colour(), slot.background(), parameter, slot.x(), slot.y(),
                slot.width(), slot.height(), slot.timing());
    }

    private static Slot withTiming(Slot slot, Timing timing) {
        return new Slot(slot.name(), slot.kind(), slot.colour(), slot.background(), slot.parameter(), slot.x(), slot.y(),
                slot.width(), slot.height(), timing);
    }

    private static Slot withBox(Slot slot, double y) {
        return new Slot(slot.name(), slot.kind(), slot.colour(), slot.background(), slot.parameter(), slot.x(), y,
                slot.width(), slot.height(), slot.timing());
    }
}
