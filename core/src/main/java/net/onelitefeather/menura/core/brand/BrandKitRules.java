package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.brand.BrandKit.Mark;
import net.onelitefeather.menura.core.brand.BrandKit.MarkVariant;
import net.onelitefeather.menura.core.brand.BrandKit.Parameter;
import net.onelitefeather.menura.core.brand.BrandKit.SafeArea;
import net.onelitefeather.menura.core.brand.BrandKit.Slot;
import net.onelitefeather.menura.core.brand.BrandKit.Template;
import net.onelitefeather.menura.core.brand.BrandKit.TextRole;
import net.onelitefeather.menura.core.brand.BrandKit.TonalRamp;
import net.onelitefeather.menura.core.brand.BrandKit.Timing;
import net.onelitefeather.menura.core.brand.BrandKit.TypographyRole;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The publication rules of a brand kit. A draft is published only when {@link #violations} is empty; every violation
 * names the element it concerns, so that a refused publication says what to fix.
 */
public final class BrandKitRules {

    /** WCAG 2.x AA for normal text. */
    public static final double AA_TEXT_RATIO = 4.5;

    /** Badge, title, captions and logo lie between these two heights of the frame, in percent. */
    public static final double SAFE_TOP_PERCENT = 14;
    public static final double SAFE_BOTTOM_PERCENT = 65;

    /** The slots kept out of the top 14 % and the bottom 35 % of the frame. */
    public static final Set<String> SAFE_ZONE_SLOTS = Set.of("badge", "title", "captions", "logo");

    /** The parameter types a template may declare. */
    public static final Set<String> PARAMETER_TYPES = Set.of("text", "image");

    /** The formats every kit provides, each with its frame size in pixels. */
    public static final Map<String, int[]> FORMATS = Map.of(
            "short-9x16", new int[] {1080, 1920},
            "long-16x9-intro", new int[] {1920, 1080},
            "long-16x9-outro", new int[] {1920, 1080},
            "thumbnail", new int[] {1280, 720},
            "social-post-image", new int[] {1080, 1080},
            "end-card", new int[] {1920, 1080},
            "overlay-theme", new int[] {1920, 1080});

    /** The format of the short, which has its own rules for slots and timing. */
    public static final String SHORT = "short-9x16";
    /** The one installation-wide join domain of the call to action. */
    public static final String JOIN_DOMAIN = "join_domain";

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final double EPSILON = 1e-9;

    private BrandKitRules() {
    }

    /**
     * Checks a draft and derives its tonal ramps. A draft with a violation is refused with Problem Details that name the
     * violations; a draft without one is returned with the tones of every seed derived now.
     */
    public static BrandKit prepare(BrandKit draft) {
        List<String> violations = violations(draft);
        if (!violations.isEmpty()) {
            throw new ProblemException(422, "Brand Kit Refused",
                    "The brand kit cannot be published: " + String.join("; ", violations) + ".");
        }
        List<TonalRamp> ramps = draft.ramps().stream()
                .map(ramp -> new TonalRamp(ramp.name(), Colour.normalise(ramp.seed()), TonalPalette.derive(ramp.seed())))
                .toList();
        return new BrandKit(draft.colours(), ramps, draft.textRoles(), draft.typography(), draft.marks(), draft.voice(),
                draft.texts(), draft.templates());
    }

    /** Every reason the draft may not be published, in the order of the kit. Empty when it may be published. */
    public static List<String> violations(BrandKit kit) {
        List<String> violations = new ArrayList<>();
        colourViolations(kit, violations);
        rampViolations(kit, violations);
        textRoleViolations(kit, violations);
        typographyViolations(kit, violations);
        markViolations(kit, violations);
        languageViolations(kit, violations);
        templateViolations(kit, violations);
        return List.copyOf(violations);
    }

    private static void colourViolations(BrandKit kit, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (ColourToken token : kit.colours()) {
            if (token.name() == null || !NAME.matcher(token.name()).matches()) {
                violations.add("colour token '" + token.name() + "' has an invalid name");
                continue;
            }
            if (!seen.add(token.name())) {
                violations.add("colour token '" + token.name() + "' is defined twice");
            }
            roleViolation(violations, "colour token '" + token.name() + "'", "light", token.light());
            roleViolation(violations, "colour token '" + token.name() + "'", "dark", token.dark());
        }
    }

    private static void roleViolation(List<String> violations, String element, String role, String value) {
        if (value == null || value.isBlank()) {
            violations.add(element + " lacks the " + role + " role");
        } else if (!Colour.isHex(value)) {
            violations.add(element + " has an invalid " + role + " value");
        }
    }

    private static void rampViolations(BrandKit kit, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (TonalRamp ramp : kit.ramps()) {
            if (!seen.add(ramp.name())) {
                violations.add("tonal ramp '" + ramp.name() + "' is defined twice");
            }
            if (!Colour.isHex(ramp.seed())) {
                violations.add("tonal ramp '" + ramp.name() + "' has an invalid seed colour");
            }
        }
    }

    private static void textRoleViolations(BrandKit kit, List<String> violations) {
        for (TextRole role : kit.textRoles()) {
            ColourToken text = kit.colour(role.colour()).orElse(null);
            ColourToken background = kit.colour(role.background()).orElse(null);
            String pair = "text role '" + role.name() + "' (" + role.colour() + " on " + role.background() + ")";
            if (text == null) {
                violations.add(pair + " refers to unknown token '" + role.colour() + "'");
            }
            if (background == null) {
                violations.add(pair + " refers to unknown token '" + role.background() + "'");
            }
            if (text == null || background == null) {
                continue;
            }
            contrastViolation(violations, pair, "light", text.light(), background.light());
            contrastViolation(violations, pair, "dark", text.dark(), background.dark());
        }
    }

    private static void contrastViolation(List<String> violations, String pair, String mode, String text, String background) {
        if (!Colour.isHex(text) || !Colour.isHex(background)) {
            return;
        }
        double ratio = Colour.contrast(text, background);
        if (ratio < AA_TEXT_RATIO) {
            violations.add(pair + " in the " + mode + " role has contrast " + format(ratio) + ":1, below WCAG AA "
                    + format(AA_TEXT_RATIO) + ":1");
        }
    }

    private static void typographyViolations(BrandKit kit, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (TypographyRole role : kit.typography()) {
            if (!seen.add(role.role())) {
                violations.add("typography role '" + role.role() + "' is defined twice");
            }
            if (role.fontFile() == null || role.fontFile().isBlank()) {
                violations.add("typography role '" + role.role() + "' has no font file");
            }
            if (role.sizePx() <= 0) {
                violations.add("typography role '" + role.role() + "' has no positive size");
            }
        }
    }

    private static void markViolations(BrandKit kit, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (Mark mark : kit.marks()) {
            if (!seen.add(mark.name())) {
                violations.add("mark '" + mark.name() + "' is defined twice");
            }
            if (mark.variants().isEmpty()) {
                violations.add("mark '" + mark.name() + "' has no variants");
            }
            for (MarkVariant variant : mark.variants()) {
                String element = "mark '" + mark.name() + "' variant '" + variant.name() + "'";
                if (variant.file() == null || variant.file().isBlank()) {
                    violations.add(element + " has no file");
                }
                if (variant.safeArea() == null) {
                    violations.add(element + " has no safe area");
                } else if (!withinPercent(variant.safeArea())) {
                    violations.add(element + " has a safe area outside 0 to 100 percent");
                }
            }
        }
    }

    private static boolean withinPercent(SafeArea area) {
        return Stream.of(area.top(), area.right(), area.bottom(), area.left())
                .allMatch(value -> value >= 0 && value <= 100);
    }

    private static void languageViolations(BrandKit kit, List<String> violations) {
        Set<String> seen = new HashSet<>();
        kit.texts().forEach(entry -> {
            if (entry.language() == null || entry.language().isBlank()) {
                violations.add("a text entry has no language");
            } else if (!seen.add(entry.language())) {
                violations.add("the language '" + entry.language() + "' is defined twice");
            }
        });
    }

    private static void templateViolations(BrandKit kit, List<String> violations) {
        Set<String> seen = new HashSet<>();
        for (Template template : kit.templates()) {
            if (!seen.add(template.format())) {
                violations.add("format '" + template.format() + "' has two templates");
            }
            if (!FORMATS.containsKey(template.format())) {
                violations.add("template for unknown format '" + template.format() + "'");
                continue;
            }
            templateViolations(kit, template, violations);
        }
        for (String format : FORMATS.keySet()) {
            if (!seen.contains(format)) {
                violations.add("no template for format '" + format + "'");
            }
        }
    }

    private static void templateViolations(BrandKit kit, Template template, List<String> violations) {
        int[] size = FORMATS.get(template.format());
        if (template.width() != size[0] || template.height() != size[1]) {
            violations.add("template '" + template.format() + "' must be " + size[0] + " by " + size[1] + " pixels");
        }
        parameterViolations(template, violations);
        Set<String> names = new HashSet<>();
        for (Slot slot : template.slots()) {
            String element = "slot '" + slot.name() + "' of template '" + template.format() + "'";
            if (!names.add(slot.name())) {
                violations.add(element + " is defined twice");
            }
            tokenViolation(kit, violations, element, "colour", slot.colour());
            tokenViolation(kit, violations, element, "background", slot.background());
            if (slot.parameter() != null && template.parameter(slot.parameter()).isEmpty()) {
                violations.add(element + " uses the undeclared parameter '" + slot.parameter() + "'");
            }
            if (slot.timing() == null) {
                violations.add(element + " has no timing");
            }
            boxViolation(violations, element, slot);
            if (SAFE_ZONE_SLOTS.contains(slot.name()) && leavesSafeZone(slot)) {
                violations.add(element + " lies in the top 14 % or bottom 35 % of the frame");
            }
        }
        if (SHORT.equals(template.format())) {
            shortViolations(template, violations);
        }
    }

    private static void parameterViolations(Template template, List<String> violations) {
        Set<String> names = new HashSet<>();
        for (Parameter parameter : template.parameters()) {
            if (!names.add(parameter.name())) {
                violations.add("parameter '" + parameter.name() + "' of template '" + template.format() + "' is defined twice");
            }
            if (!PARAMETER_TYPES.contains(parameter.type())) {
                violations.add("parameter '" + parameter.name() + "' of template '" + template.format()
                        + "' has the unknown type '" + parameter.type() + "'");
            }
        }
    }

    private static void tokenViolation(BrandKit kit, List<String> violations, String element, String use, String token) {
        if (token == null) {
            return;
        }
        if (Colour.isHex(token) || token.startsWith("#")) {
            violations.add(element + " holds the raw " + use + " value '" + token + "', use a colour token");
        } else if (kit.colour(token).isEmpty()) {
            violations.add(element + " refers to the unknown " + use + " token '" + token + "'");
        }
    }

    private static void boxViolation(List<String> violations, String element, Slot slot) {
        boolean inside = slot.x() >= 0 && slot.y() >= 0 && slot.width() > 0 && slot.height() > 0
                && slot.x() + slot.width() <= 100 + EPSILON && slot.y() + slot.height() <= 100 + EPSILON;
        if (!inside) {
            violations.add(element + " lies outside the frame");
        }
    }

    private static boolean leavesSafeZone(Slot slot) {
        return slot.y() < SAFE_TOP_PERCENT || slot.y() + slot.height() > SAFE_BOTTOM_PERCENT;
    }

    /** The rules of the short 9:16: its slots, the timing of each, the call to action and no end card. */
    private static void shortViolations(Template template, List<String> violations) {
        Map<String, Slot> slots = new HashMap<>();
        template.slots().forEach(slot -> slots.putIfAbsent(slot.name(), slot));
        for (String required : List.of("badge", "title", "captions", "session-tiles", "cta")) {
            if (!slots.containsKey(required)) {
                violations.add("the short template lacks the slot '" + required + "'");
            }
        }
        if (slots.containsKey("end-card")) {
            violations.add("the short template contains an end card");
        }
        requireTiming(slots.get("badge"), violations, "badge must be visible for the whole duration",
                timing -> timing.from() == 0 && timing.until() == null && !timing.fromEnd());
        requireTiming(slots.get("title"), violations, "title must be shown only from 0 to 3 seconds",
                timing -> timing.from() == 0 && timing.until() != null && timing.until() == 3 && !timing.fromEnd());
        requireTiming(slots.get("cta"), violations, "cta must be shown only during the last 2 seconds",
                timing -> timing.from() == 2 && timing.until() == null && timing.fromEnd());
        Slot cta = slots.get("cta");
        if (cta != null) {
            Optional<Parameter> joinDomain = template.parameter(JOIN_DOMAIN);
            if (!JOIN_DOMAIN.equals(cta.parameter()) || joinDomain.isEmpty() || !joinDomain.get().required()
                    || !"text".equals(joinDomain.get().type())) {
                violations.add("the cta requires the required text parameter " + JOIN_DOMAIN);
            }
        }
    }

    private static void requireTiming(Slot slot, List<String> violations, String rule, Predicate<Timing> ok) {
        if (slot != null && (slot.timing() == null || !ok.test(slot.timing()))) {
            violations.add(rule);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
