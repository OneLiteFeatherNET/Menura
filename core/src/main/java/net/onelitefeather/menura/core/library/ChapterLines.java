package net.onelitefeather.menura.core.library;

import java.util.Comparator;
import java.util.List;

/**
 * The YouTube chapter lines of a timeline's marks: one line per mark, in playback order, each a timestamp and the title.
 * The timestamp is mm:ss below an hour and h:mm:ss from an hour on.
 */
public final class ChapterLines {

    private static final int HOUR = 3600;
    private static final int MINUTE = 60;

    private ChapterLines() {
    }

    /** The timestamp of a second of the video, as YouTube reads it in a description. */
    public static String timestamp(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("a chapter cannot start before 00:00");
        }
        int hours = seconds / HOUR;
        int minutes = seconds % HOUR / MINUTE;
        int rest = seconds % MINUTE;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, rest) : "%02d:%02d".formatted(minutes, rest);
    }

    /** One line per mark, in ascending order of their seconds: the timestamp, a space and the title on one line. */
    public static List<String> lines(List<ChapterMark> marks) {
        return marks.stream()
                .sorted(Comparator.comparingInt(ChapterMark::seconds))
                .map(mark -> timestamp(mark.seconds()) + " " + mark.title().strip().replaceAll("\\s+", " "))
                .toList();
    }

    /**
     * The description followed by a blank line and the chapter lines. Without marks the description is returned unchanged;
     * without a description the lines stand alone.
     */
    public static String appendTo(String description, List<ChapterMark> marks) {
        if (marks.isEmpty()) {
            return description;
        }
        String chapters = String.join("\n", lines(marks));
        return description == null || description.isEmpty() ? chapters : description + "\n\n" + chapters;
    }
}
