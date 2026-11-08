package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.post.ExternalPostFormat;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/**
 * The format rule of YouTube items. The Data API has no field that marks a Short, and the orientation of a video is only
 * readable by its owner, so the rule uses the duration that {@code videos.list} reports in {@code contentDetails.duration}
 * (ISO 8601). YouTube's Shorts help page states that Shorts are "up to 3 minutes long" (checked 2026-11-08,
 * https://support.google.com/youtube/answer/10059070). A video of at most three minutes is therefore a {@code short};
 * anything longer, or without a readable duration, is a {@code video}. Known limitation: a horizontal video of three
 * minutes or less is also recorded as a short, because the public API does not expose its orientation.
 */
final class YouTubeFormat {

    /** The longest duration of a Short, inclusive. */
    static final Duration SHORT_LIMIT = Duration.ofMinutes(3);

    private YouTubeFormat() {
    }

    /** The external post format for a {@code contentDetails.duration} value, or {@code video} when it cannot be read. */
    static String of(String isoDuration) {
        if (isoDuration == null || isoDuration.isBlank()) {
            return ExternalPostFormat.VIDEO;
        }
        try {
            Duration length = Duration.parse(isoDuration);
            return length.compareTo(SHORT_LIMIT) <= 0 ? ExternalPostFormat.SHORT : ExternalPostFormat.VIDEO;
        } catch (DateTimeParseException notIso8601) {
            return ExternalPostFormat.VIDEO;
        }
    }
}
