package net.onelitefeather.menura.core.brand;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * sRGB colours of the brand kit: the value form {@code #rrggbb} and the WCAG 2.x contrast ratio between two of them.
 */
public final class Colour {

    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private Colour() {
    }

    /** Whether the value is a colour in the form {@code #rrggbb}. */
    public static boolean isHex(String value) {
        return value != null && HEX.matcher(value).matches();
    }

    /** The canonical form of a colour value: lower case with a leading hash. */
    public static String normalise(String hex) {
        return hex.toLowerCase(Locale.ROOT);
    }

    /** The WCAG relative luminance of a colour value. */
    public static double luminance(String hex) {
        double red = linear(channel(hex, 1));
        double green = linear(channel(hex, 3));
        double blue = linear(channel(hex, 5));
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    /** The WCAG contrast ratio of two colour values, from 1 (no contrast) to 21 (black on white). */
    public static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static int channel(String hex, int offset) {
        return Integer.parseInt(hex.substring(offset, offset + 2), 16);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
