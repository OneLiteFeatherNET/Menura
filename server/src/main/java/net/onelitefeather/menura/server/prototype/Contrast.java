package net.onelitefeather.menura.server.prototype;

import java.util.Locale;
import java.util.regex.Pattern;

/** WCAG 2.x contrast ratio between two sRGB colours written as {@code #RRGGBB}. */
final class Contrast {

    /** WCAG AA for normal text. */
    static final double MINIMUM = 4.5;

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private Contrast() {
    }

    static boolean isHex(String value) {
        return value != null && HEX.matcher(value).matches();
    }

    /** The ratio (L1 + 0.05) / (L2 + 0.05) with the lighter colour as L1; always at least 1. */
    static double ratio(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** The WCAG relative luminance of a {@code #RRGGBB} colour. */
    static double luminance(String hex) {
        String digits = hex.substring(1).toUpperCase(Locale.ROOT);
        double red = linear(Integer.parseInt(digits.substring(0, 2), 16));
        double green = linear(Integer.parseInt(digits.substring(2, 4), 16));
        double blue = linear(Integer.parseInt(digits.substring(4, 6), 16));
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
