package net.onelitefeather.menura.core.brand;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;

import java.util.StringJoiner;

/**
 * The CSS export of a kit version: every colour token as a custom property, with its light and dark values. The light
 * values are the default; the dark values apply by the system preference unless the page selects a theme explicitly.
 */
public final class BrandKitCss {

    private static final String PREFIX = "--brand-";

    private BrandKitCss() {
    }

    public static String of(BrandKit kit) {
        StringBuilder css = new StringBuilder();
        css.append("/* Brand kit export. Generated from a published version; do not edit by hand. */\n");
        css.append(":root, :root[data-theme=\"light\"] {\n").append(declarations(kit, false, "  ")).append("}\n");
        css.append("@media (prefers-color-scheme: dark) {\n");
        css.append("  :root:not([data-theme=\"light\"]) {\n").append(declarations(kit, true, "    ")).append("  }\n");
        css.append("}\n");
        css.append(":root[data-theme=\"dark\"] {\n").append(declarations(kit, true, "  ")).append("}\n");
        return css.toString();
    }

    private static String declarations(BrandKit kit, boolean dark, String indent) {
        StringJoiner lines = new StringJoiner("");
        for (ColourToken token : kit.colours()) {
            lines.add(indent + PREFIX + token.name() + ": " + (dark ? token.dark() : token.light()) + ";\n");
        }
        return lines.toString();
    }
}
