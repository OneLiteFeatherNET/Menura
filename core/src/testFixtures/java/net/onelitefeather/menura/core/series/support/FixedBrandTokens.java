package net.onelitefeather.menura.core.series.support;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.series.BrandTokens;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A brand kit with the colour tokens and templates a test sets, and nothing else. Its values never change in a test. */
public final class FixedBrandTokens implements BrandTokens {

    private final Map<String, ColourToken> colours = new HashMap<>();
    private final Set<String> templates = new HashSet<>();

    /** Defines a colour token with the same value for the light and the dark role. */
    public FixedBrandTokens colour(String name, String hex) {
        return colour(name, hex, hex);
    }

    public FixedBrandTokens colour(String name, String light, String dark) {
        colours.put(name, new ColourToken(name, light, dark));
        return this;
    }

    public FixedBrandTokens template(String format) {
        templates.add(format);
        return this;
    }

    @Override
    public Optional<ColourToken> colour(String name) {
        return Optional.ofNullable(colours.get(name));
    }

    @Override
    public boolean hasTemplate(String format) {
        return templates.contains(format);
    }
}
