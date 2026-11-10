package net.onelitefeather.menura.core.config;

import net.onelitefeather.menura.core.channel.youtube.YouTubeMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelSettingsTest {

    @Test
    void theYouTubeModeDefaultsToLive() {
        assertEquals(YouTubeMode.LIVE, ChannelSettings.from(Map.of()).youtubeMode(),
                "without MENURA_YOUTUBE_MODE the adapter talks to YouTube");
    }

    @Test
    void theYouTubeModeIsReadFromTheEnvironment() {
        assertEquals(YouTubeMode.SIMULATED,
                ChannelSettings.from(Map.of("MENURA_YOUTUBE_MODE", "simulated")).youtubeMode(),
                "the variable selects the simulated mode");
    }

    @Test
    void anUnknownYouTubeModeStopsTheSettingsAtOnce() {
        assertThrows(IllegalArgumentException.class,
                () -> ChannelSettings.from(Map.of("MENURA_YOUTUBE_MODE", "foo")),
                "a mistyped mode is refused instead of silently running live");
    }
}
