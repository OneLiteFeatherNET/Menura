package net.onelitefeather.menura.core.config;

import net.onelitefeather.menura.core.channel.youtube.YouTubeMode;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Settings of the channel connections that the jobs need, read from environment variables with the same names the
 * server reads through its configuration. The token key has no default: a job started without it fails at once.
 */
public record ChannelSettings(String tokenKey, String previousTokenKey, Duration refreshMargin,
                              String youtubeClientId, String youtubeClientSecret, String youtubeRedirectUri,
                              YouTubeMode youtubeMode) {

    public static final String TOKEN_KEY_VARIABLE = "MENURA_TOKEN_ENCRYPTION_KEY";
    public static final String PREVIOUS_TOKEN_KEY_VARIABLE = "MENURA_TOKEN_ENCRYPTION_KEY_PREVIOUS";
    public static final String REFRESH_MARGIN_VARIABLE = "MENURA_CHANNEL_REFRESH_MARGIN";
    public static final String YOUTUBE_CLIENT_ID_VARIABLE = "MENURA_YOUTUBE_CLIENT_ID";
    public static final String YOUTUBE_CLIENT_SECRET_VARIABLE = "MENURA_YOUTUBE_CLIENT_SECRET";
    public static final String YOUTUBE_REDIRECT_URI_VARIABLE = "MENURA_YOUTUBE_REDIRECT_URI";
    public static final String YOUTUBE_MODE_VARIABLE = YouTubeMode.VARIABLE;

    static final String DEFAULT_REFRESH_MARGIN = "PT10M";
    static final String DEFAULT_YOUTUBE_REDIRECT_URI = "http://localhost:8080/channel-connections/callback";

    public ChannelSettings {
        Objects.requireNonNull(tokenKey, "tokenKey");
        Objects.requireNonNull(refreshMargin, "refreshMargin");
        Objects.requireNonNull(youtubeClientId, "youtubeClientId");
        Objects.requireNonNull(youtubeClientSecret, "youtubeClientSecret");
        Objects.requireNonNull(youtubeRedirectUri, "youtubeRedirectUri");
        Objects.requireNonNull(youtubeMode, "youtubeMode");
    }

    /** Reads the settings from an environment map, normally {@code System.getenv()}. */
    public static ChannelSettings from(Map<String, String> environment) {
        return new ChannelSettings(
                environment.getOrDefault(TOKEN_KEY_VARIABLE, ""),
                environment.getOrDefault(PREVIOUS_TOKEN_KEY_VARIABLE, ""),
                Duration.parse(environment.getOrDefault(REFRESH_MARGIN_VARIABLE, DEFAULT_REFRESH_MARGIN)),
                environment.getOrDefault(YOUTUBE_CLIENT_ID_VARIABLE, ""),
                environment.getOrDefault(YOUTUBE_CLIENT_SECRET_VARIABLE, ""),
                environment.getOrDefault(YOUTUBE_REDIRECT_URI_VARIABLE, DEFAULT_YOUTUBE_REDIRECT_URI),
                YouTubeMode.parse(environment.get(YOUTUBE_MODE_VARIABLE)));
    }

    /** The text form never shows the keys or the secret. */
    @Override
    public String toString() {
        return "ChannelSettings[tokenKey=redacted, previousTokenKey=redacted, refreshMargin=" + refreshMargin
                + ", youtubeClientId=" + youtubeClientId + ", youtubeClientSecret=redacted, youtubeRedirectUri="
                + youtubeRedirectUri + ", youtubeMode=" + youtubeMode + "]";
    }
}
