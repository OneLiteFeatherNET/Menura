package net.onelitefeather.menura.core.attribution;

import java.util.Arrays;
import java.util.Optional;

/**
 * The fixed choices a player may name as the source of a first join (change add-join-attribution). Only the four
 * platforms restrict the post attribution; {@code friends} and {@code other} do not. Any other value is refused.
 */
public enum StatedSource {
    YOUTUBE("youtube", true),
    TIKTOK("tiktok", true),
    INSTAGRAM("instagram", true),
    TWITCH("twitch", true),
    FRIENDS("friends", false),
    OTHER("other", false);

    private final String wire;
    private final boolean platform;

    StatedSource(String wire, boolean platform) {
        this.wire = wire;
        this.platform = platform;
    }

    /** The lower-case value the proxy sends and the database stores. */
    public String wire() {
        return wire;
    }

    /** True for a platform, whose name matches the platform of an external post. */
    public boolean isPlatform() {
        return platform;
    }

    /** The choice with exactly this lower-case value; anything else, including free text, yields empty. */
    public static Optional<StatedSource> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(source -> source.wire.equals(value)).findFirst();
    }
}
