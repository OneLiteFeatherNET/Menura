package net.onelitefeather.menura.core.series;

import java.util.regex.Pattern;

/**
 * Finds a series hashtag in a text. The tag matches as a whole word: {@code #kurz} does not match {@code #kurzzeit}, and
 * the case of the letters does not matter.
 */
public final class SeriesHashtags {

    private SeriesHashtags() {
    }

    /** Whether the text contains the hashtag as a whole word. */
    public static boolean contains(String text, String hashtag) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(hashtag) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(text).find();
    }
}
