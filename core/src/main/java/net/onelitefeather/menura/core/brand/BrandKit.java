package net.onelitefeather.menura.core.brand;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole brand kit of one version: named colour tokens with light and dark roles, the tonal ramps derived from
 * the seeds, the text roles checked for contrast, typography, marks, voice and per-language texts, and the layout
 * templates. A version is stored and read as one value; nothing in it is a reference to another version.
 *
 * <p>The tones of a ramp are an output of publication ({@link BrandKitRules#prepare}); a draft carries the seeds and
 * no tones.
 */
public record BrandKit(List<ColourToken> colours, List<TonalRamp> ramps, List<TextRole> textRoles,
                       List<TypographyRole> typography, List<Mark> marks, String voice, List<LanguageText> texts,
                       List<Template> templates) {

    public BrandKit {
        colours = copy(colours);
        ramps = copy(ramps);
        textRoles = copy(textRoles);
        typography = copy(typography);
        marks = copy(marks);
        texts = copy(texts);
        templates = copy(templates);
    }

    /** The colour token of this name, if the kit defines it. */
    public Optional<ColourToken> colour(String name) {
        return colours.stream().filter(token -> token.name().equals(name)).findFirst();
    }

    /** The end-card text and hashtags of one language. A language without an entry is empty, never substituted. */
    public Optional<LanguageText> text(String language) {
        return texts.stream().filter(entry -> entry.language().equals(language)).findFirst();
    }

    /** The template of one format, if the kit has one. */
    public Optional<Template> template(String format) {
        return templates.stream().filter(template -> template.format().equals(format)).findFirst();
    }

    /** A named colour with its value for the light role and its value for the dark role. */
    public record ColourToken(String name, String light, String dark) {
    }

    /** A seed colour and the tones derived from it at publication. */
    public record TonalRamp(String name, String seed, List<Tone> tones) {

        public TonalRamp {
            tones = copy(tones);
        }
    }

    /** One tone of a ramp: 0 is black, 100 is white, and the value is a colour in the form {@code #rrggbb}. */
    public record Tone(int tone, String hex) {
    }

    /** A text colour token shown on a background token; both must reach WCAG AA in both roles. */
    public record TextRole(String name, String colour, String background) {
    }

    /** The font file and size of one typography role, such as headline, body or caption. */
    public record TypographyRole(String role, String fontFile, int sizePx) {
    }

    /** A logo or mark with named variants; each variant has its own safe area. */
    public record Mark(String name, List<MarkVariant> variants) {

        public Mark {
            variants = copy(variants);
        }
    }

    /** One variant of a mark: its file and the margin, in percent of the mark's frame, that no other element may enter. */
    public record MarkVariant(String name, String file, SafeArea safeArea) {
    }

    /** A safe area in percent of the frame, per side. */
    public record SafeArea(double top, double right, double bottom, double left) {
    }

    /** The hashtags and end-card text of one language. */
    public record LanguageText(String language, String endCard, List<String> hashtags) {

        public LanguageText {
            hashtags = copy(hashtags);
        }
    }

    /**
     * A layout template of one format. It refers to brand tokens only and declares every parameter a person fills in.
     */
    public record Template(String format, String name, int width, int height, List<Slot> slots,
                           List<Parameter> parameters) {

        public Template {
            slots = copy(slots);
            parameters = copy(parameters);
        }

        public Optional<Parameter> parameter(String name) {
            return parameters.stream().filter(parameter -> parameter.name().equals(name)).findFirst();
        }
    }

    /**
     * One element of a template. Boxes are in percent of the frame, from its top left corner. A colour or background
     * is the name of a token, never a raw value. A slot with a parameter is filled at render time.
     */
    public record Slot(String name, String kind, String colour, String background, String parameter, double x,
                       double y, double width, double height, Timing timing) {
    }

    /**
     * When a slot is shown, in seconds of the output. A timing anchored to the end counts its values back from the end
     * of the output. An {@code until} of null lasts to the end of the output.
     */
    public record Timing(double from, Double until, boolean fromEnd) {

        /** Whether the slot is shown at this second of an output of the given duration. */
        public boolean visibleAt(double second, double duration) {
            double start = fromEnd ? duration - from : from;
            double end = until == null ? duration : (fromEnd ? duration - until : until);
            return second >= start && second < end;
        }
    }

    /** A declared parameter of a template: its name, its type (text or image) and whether it must be supplied. */
    public record Parameter(String name, String type, boolean required) {
    }

    /** A defensive copy without null entries: a null entry in a request is simply absent from the kit. */
    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
