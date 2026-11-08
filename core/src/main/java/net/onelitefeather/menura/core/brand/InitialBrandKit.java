package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.brand.BrandKit.Mark;
import net.onelitefeather.menura.core.brand.BrandKit.MarkVariant;
import net.onelitefeather.menura.core.brand.BrandKit.Parameter;
import net.onelitefeather.menura.core.brand.BrandKit.SafeArea;
import net.onelitefeather.menura.core.brand.BrandKit.Slot;
import net.onelitefeather.menura.core.brand.BrandKit.Template;
import net.onelitefeather.menura.core.brand.BrandKit.TextRole;
import net.onelitefeather.menura.core.brand.BrandKit.Timing;
import net.onelitefeather.menura.core.brand.BrandKit.TonalRamp;

import java.util.List;

/**
 * The first kit version. Its colour roles are the role values of the stream overlay theme (the Material Design 3
 * scheme generated from the seed {@value #SEED} in web/src/theme/seed.json, light and dark), and its mark is the feather
 * logo. No colour appears here that is not a token of the kit: the templates refer to tokens only.
 *
 * <p>Typography, voice and the per-language texts are not part of this seed: the font files and the copy are supplied by
 * the marketing owner and are published as a later version. The mark files are referenced by name; the files
 * themselves are uploaded with the mark.
 */
public final class InitialBrandKit {

    /** The seed colour of the overlay theme. */
    public static final String SEED = "#6A3DE8";

    private InitialBrandKit() {
    }

    /** The draft of the first version, before publication derives its tones. */
    public static BrandKit create() {
        return new BrandKit(colours(), List.of(new TonalRamp("brand", SEED, List.of())), textRoles(), List.of(),
                List.of(new Mark("feather", List.of(
                        new MarkVariant("light", "marks/feather-light.svg", margin()),
                        new MarkVariant("dark", "marks/feather-dark.svg", margin())))),
                "", List.of(), templates());
    }

    private static SafeArea margin() {
        return new SafeArea(10, 10, 10, 10);
    }

    private static List<ColourToken> colours() {
        return List.of(
                token("primary", "#6638e4", "#ccbdff"),
                token("on-primary", "#ffffff", "#360096"),
                token("primary-container", "#e7deff", "#4e0ccc"),
                token("on-primary-container", "#1f005f", "#e7deff"),
                token("secondary", "#615b71", "#cac3dc"),
                token("on-secondary", "#ffffff", "#322e41"),
                token("secondary-container", "#e7dff8", "#494458"),
                token("on-secondary-container", "#1d192b", "#e7dff8"),
                token("tertiary", "#7d5262", "#eeb8ca"),
                token("on-tertiary", "#ffffff", "#492534"),
                token("tertiary-container", "#ffd9e4", "#623b4a"),
                token("on-tertiary-container", "#31111e", "#ffd9e4"),
                token("error", "#ba1a1a", "#ffb4ab"),
                token("on-error", "#ffffff", "#690005"),
                token("error-container", "#ffdad6", "#93000a"),
                token("on-error-container", "#410002", "#ffb4ab"),
                token("background", "#fffbff", "#1c1b1e"),
                token("on-background", "#1c1b1e", "#e6e1e6"),
                token("surface", "#fffbff", "#1c1b1e"),
                token("on-surface", "#1c1b1e", "#e6e1e6"),
                token("surface-variant", "#e6e0ec", "#48454e"),
                token("on-surface-variant", "#48454e", "#cac4cf"),
                token("outline", "#79757f", "#938f99"),
                token("outline-variant", "#cac4cf", "#48454e"),
                token("shadow", "#000000", "#000000"),
                token("scrim", "#000000", "#000000"),
                token("inverse-surface", "#313033", "#e6e1e6"),
                token("inverse-on-surface", "#f4eff4", "#313033"),
                token("inverse-primary", "#ccbdff", "#6638e4"));
    }

    private static ColourToken token(String name, String light, String dark) {
        return new ColourToken(name, light, dark);
    }

    private static List<TextRole> textRoles() {
        return List.of(
                new TextRole("on-primary", "on-primary", "primary"),
                new TextRole("on-primary-container", "on-primary-container", "primary-container"),
                new TextRole("on-secondary", "on-secondary", "secondary"),
                new TextRole("on-secondary-container", "on-secondary-container", "secondary-container"),
                new TextRole("on-tertiary", "on-tertiary", "tertiary"),
                new TextRole("on-tertiary-container", "on-tertiary-container", "tertiary-container"),
                new TextRole("on-error", "on-error", "error"),
                new TextRole("on-error-container", "on-error-container", "error-container"),
                new TextRole("on-background", "on-background", "background"),
                new TextRole("on-surface", "on-surface", "surface"),
                new TextRole("on-surface-variant", "on-surface-variant", "surface-variant"),
                new TextRole("inverse-on-surface", "inverse-on-surface", "inverse-surface"));
    }

    private static List<Template> templates() {
        return List.of(
                short9x16(),
                template("long-16x9-intro", "Long intro", 1920, 1080,
                        List.of(background(), image(55, 10, 40, 40), text("title", "on-surface", 8, 30, 84, 18, "title"),
                                text("subtitle", "on-surface-variant", 8, 50, 84, 8, "subtitle")),
                        titleParameters()),
                template("long-16x9-outro", "Long outro", 1920, 1080,
                        List.of(background(), image(55, 10, 40, 40), text("title", "on-surface", 8, 30, 84, 18, "title"),
                                text("subtitle", "on-surface-variant", 8, 50, 84, 8, "subtitle")),
                        titleParameters()),
                template("thumbnail", "Thumbnail", 1280, 720,
                        List.of(background(), image(0, 0, 100, 100), text("title", "on-surface", 6, 20, 60, 40, "title")),
                        titleParameters()),
                template("social-post-image", "Social post image", 1080, 1080,
                        List.of(background(), image(0, 0, 100, 100), text("title", "on-surface", 6, 20, 88, 30, "title")),
                        titleParameters()),
                template("end-card", "End card", 1920, 1080,
                        List.of(background(), image(55, 10, 40, 40), text("title", "on-surface", 8, 25, 84, 20, "title"),
                                text("subtitle", "on-surface-variant", 8, 46, 84, 10, "subtitle")),
                        titleParameters()),
                template("overlay-theme", "Overlay theme", 1920, 1080,
                        List.of(background(), new Slot("accent", "shape", null, "primary", null, 0, 0, 100, 2, always())),
                        List.of()));
    }

    /** The short 9:16: a badge for the whole duration, a title for the first three seconds and a call to action in the last two. */
    private static Template short9x16() {
        return template("short-9x16", "Short 9:16", 1080, 1920,
                List.of(background(),
                        new Slot("badge", "text", "on-primary", "primary", null, 4, 16, 30, 5, always()),
                        new Slot("logo", "image", null, null, null, 80, 15, 16, 5, always()),
                        new Slot("title", "text", "on-surface", null, "title", 4, 22, 92, 14,
                                new Timing(0, 3.0, false)),
                        new Slot("captions", "text", "on-surface", null, null, 4, 40, 92, 20, always()),
                        new Slot("session-tiles", "tiles", null, null, null, 4, 66, 92, 20, always()),
                        new Slot("cta", "text", "on-primary", "primary", BrandKitRules.JOIN_DOMAIN, 4, 88, 92, 6,
                                new Timing(2, null, true))),
                List.of(new Parameter("title", "text", true), new Parameter("subtitle", "text", false),
                        new Parameter("image", "image", false), new Parameter(BrandKitRules.JOIN_DOMAIN, "text", true)));
    }

    private static Template template(String format, String name, int width, int height, List<Slot> slots,
                                     List<Parameter> parameters) {
        return new Template(format, name, width, height, slots, parameters);
    }

    private static List<Parameter> titleParameters() {
        return List.of(new Parameter("title", "text", true), new Parameter("subtitle", "text", false),
                new Parameter("image", "image", false));
    }

    private static Slot background() {
        return new Slot("background", "shape", null, "surface", null, 0, 0, 100, 100, always());
    }

    private static Slot image(double x, double y, double width, double height) {
        return new Slot("image", "image", null, null, "image", x, y, width, height, always());
    }

    private static Slot text(String name, String colour, double x, double y, double width, double height, String parameter) {
        return new Slot(name, "text", colour, null, parameter, x, y, width, height, always());
    }

    private static Timing always() {
        return new Timing(0, null, false);
    }
}
