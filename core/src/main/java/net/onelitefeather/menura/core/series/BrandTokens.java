package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;

import java.util.Optional;

/**
 * What the series need from the brand kit: its colour tokens for the contrast check, and whether it has a layout template
 * of a given format. The server answers from the newest published version.
 */
public interface BrandTokens {

    /** The colour token of this name in the current brand kit, if the kit defines it. */
    Optional<ColourToken> colour(String name);

    /** Whether the current brand kit has a layout template of this format. */
    boolean hasTemplate(String format);
}
