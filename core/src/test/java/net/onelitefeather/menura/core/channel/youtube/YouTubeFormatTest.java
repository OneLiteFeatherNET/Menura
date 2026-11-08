package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.post.ExternalPostFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YouTubeFormatTest {

    @Test
    void aVideoOfExactlyThreeMinutesIsAShort() {
        assertEquals(ExternalPostFormat.SHORT, YouTubeFormat.of("PT3M"), "the Shorts limit of three minutes is inclusive");
    }

    @Test
    void aVideoOfFortyFiveSecondsIsAShort() {
        assertEquals(ExternalPostFormat.SHORT, YouTubeFormat.of("PT45S"), "a short clip is recorded as a short");
    }

    @Test
    void aVideoLongerThanThreeMinutesIsAVideo() {
        assertEquals(ExternalPostFormat.VIDEO, YouTubeFormat.of("PT3M1S"), "one second over the limit makes it a video");
    }

    @Test
    void aLongVideoIsAVideo() {
        assertEquals(ExternalPostFormat.VIDEO, YouTubeFormat.of("PT15M33S"), "a long upload is recorded as a video");
    }

    @Test
    void aVideoWithoutADurationIsAVideoAndNeverCountedAsAShort() {
        assertEquals(ExternalPostFormat.VIDEO, YouTubeFormat.of(null), "a missing duration is not evidence of a short");
    }

    @Test
    void aVideoWithAnUnreadableDurationIsAVideo() {
        assertEquals(ExternalPostFormat.VIDEO, YouTubeFormat.of("not-a-duration"), "an unreadable duration is a video");
    }
}
