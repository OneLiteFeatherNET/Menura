package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.Tone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Derives the tones of a seed colour. The result is a pure function of the seed: the same seed always yields the same
 * tones, and the tones are stored in the kit version so that later changes of this function never alter a version.
 *
 * <p>The hue and saturation come from the seed; each tone sets the lightness to its own value, in HSL. This is an
 * approximation (Näherung) of the Material Design 3 HCT tone steps, which is not implemented here; it stays until a
 * reviewed HCT implementation is adopted (see the design decisions of the change add-brand-kit).
 */
public final class TonalPalette {

    /** The tone steps of a ramp, from black to white. */
    static final int[] STEPS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100};

    private TonalPalette() {
    }

    /** The tones of a seed colour, in the order of {@link #STEPS}. The seed must be a valid colour value. */
    public static List<Tone> derive(String seedHex) {
        int red = Integer.parseInt(seedHex.substring(1, 3), 16);
        int green = Integer.parseInt(seedHex.substring(3, 5), 16);
        int blue = Integer.parseInt(seedHex.substring(5, 7), 16);
        double[] hsl = toHsl(red / 255.0, green / 255.0, blue / 255.0);
        List<Tone> tones = new ArrayList<>();
        for (int step : STEPS) {
            tones.add(new Tone(step, fromHsl(hsl[0], hsl[1], step / 100.0)));
        }
        return List.copyOf(tones);
    }

    /** Hue in degrees, saturation and lightness in [0, 1]. */
    private static double[] toHsl(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double lightness = (max + min) / 2;
        double delta = max - min;
        if (delta == 0) {
            return new double[] {0, 0, lightness};
        }
        double saturation = delta / (1 - Math.abs(2 * lightness - 1));
        double hue;
        if (max == r) {
            hue = 60 * (((g - b) / delta) % 6);
        } else if (max == g) {
            hue = 60 * ((b - r) / delta + 2);
        } else {
            hue = 60 * ((r - g) / delta + 4);
        }
        return new double[] {(hue + 360) % 360, saturation, lightness};
    }

    private static String fromHsl(double hue, double saturation, double lightness) {
        double chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double sector = hue / 60;
        double second = chroma * (1 - Math.abs(sector % 2 - 1));
        double[] rgb;
        if (sector < 1) {
            rgb = new double[] {chroma, second, 0};
        } else if (sector < 2) {
            rgb = new double[] {second, chroma, 0};
        } else if (sector < 3) {
            rgb = new double[] {0, chroma, second};
        } else if (sector < 4) {
            rgb = new double[] {0, second, chroma};
        } else if (sector < 5) {
            rgb = new double[] {second, 0, chroma};
        } else {
            rgb = new double[] {chroma, 0, second};
        }
        double offset = lightness - chroma / 2;
        return String.format(Locale.ROOT, "#%02x%02x%02x", channel(rgb[0] + offset), channel(rgb[1] + offset),
                channel(rgb[2] + offset));
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
}
