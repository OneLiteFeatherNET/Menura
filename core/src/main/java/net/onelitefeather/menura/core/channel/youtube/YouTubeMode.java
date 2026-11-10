package net.onelitefeather.menura.core.channel.youtube;

import java.util.Locale;

/**
 * Whether the YouTube adapter talks to YouTube ({@link #LIVE}, the default) or runs against the locally generated data of
 * {@link SimulatedYouTube} ({@link #SIMULATED}, for development only). Read from {@code MENURA_YOUTUBE_MODE}.
 */
public enum YouTubeMode {

    LIVE,
    SIMULATED;

    /** The environment variable that selects the mode. */
    public static final String VARIABLE = "MENURA_YOUTUBE_MODE";

    /** The mode of a configured value: blank means {@link #LIVE}; anything other than the two names is refused. */
    public static YouTubeMode parse(String value) {
        if (value == null || value.isBlank()) {
            return LIVE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "live" -> LIVE;
            case "simulated" -> SIMULATED;
            default -> throw new IllegalArgumentException(VARIABLE + " must be one of live or simulated, not '"
                    + value + "'");
        };
    }
}
