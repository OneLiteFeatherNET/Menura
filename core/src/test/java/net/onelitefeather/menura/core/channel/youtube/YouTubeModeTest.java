package net.onelitefeather.menura.core.channel.youtube;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeModeTest {

    @Test
    void aBlankValueMeansLive() {
        assertEquals(YouTubeMode.LIVE, YouTubeMode.parse(null), "an unset variable is the live mode");
        assertEquals(YouTubeMode.LIVE, YouTubeMode.parse(""), "an empty variable is the live mode");
        assertEquals(YouTubeMode.LIVE, YouTubeMode.parse("   "), "a blank variable is the live mode");
    }

    @Test
    void simulatedIsParsedIgnoringCase() {
        assertEquals(YouTubeMode.SIMULATED, YouTubeMode.parse("SIMULATED"), "the upper-case name is accepted");
    }

    @Test
    void liveIsParsedIgnoringCase() {
        assertEquals(YouTubeMode.LIVE, YouTubeMode.parse("Live"), "the mixed-case name is accepted");
    }

    @Test
    void anUnknownModeIsRefusedWithAMessageThatNamesTheVariable() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> YouTubeMode.parse("foo"), "a value other than live or simulated is refused");

        assertTrue(refused.getMessage().contains("MENURA_YOUTUBE_MODE"),
                "the message names the variable: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("live") && refused.getMessage().contains("simulated"),
                "the message names the allowed values: " + refused.getMessage());
    }
}
