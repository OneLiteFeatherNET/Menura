package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** A free tag: lower-case letters, digits and hyphens, 2 to 30 characters; at most 20 per item. */
public final class FreeTag {

    public static final int MAX_PER_ITEM = 20;
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9-]{2,30}$");

    private FreeTag() {
    }

    /** Returns the tag when it matches the pattern, and refuses it otherwise. */
    public static String parse(String tag) {
        if (tag == null || !PATTERN.matcher(tag).matches()) {
            throw new ProblemException(400, "Invalid tag", "A tag must match ^[a-z0-9-]{2,30}$, but got '"
                    + tag + "'.");
        }
        return tag;
    }

    /** Parses every tag of an item, drops duplicates and checks the limit of tags per item. */
    public static Set<String> parseAll(Collection<String> tags) {
        Set<String> parsed = new TreeSet<>();
        for (String tag : tags) {
            parsed.add(parse(tag));
        }
        requireWithinLimit(parsed);
        return parsed;
    }

    /** Refuses an item that would hold more than {@link #MAX_PER_ITEM} tags. */
    public static void requireWithinLimit(Set<String> tags) {
        if (tags.size() > MAX_PER_ITEM) {
            throw new ProblemException(400, "Invalid tag", "An item holds at most " + MAX_PER_ITEM + " free tags.");
        }
    }
}
