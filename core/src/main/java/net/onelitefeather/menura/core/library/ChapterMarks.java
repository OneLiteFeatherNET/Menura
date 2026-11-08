package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.List;

/**
 * The rules of the chapter marks of a timeline: ascending, the first at 00:00, at least ten seconds apart. Any other
 * list is refused with a problem that names the chapter rule.
 */
public final class ChapterMarks {

    /** The shortest chapter, in seconds, that the YouTube description accepts. */
    public static final int MIN_GAP_SECONDS = 10;
    private static final int MAX_TITLE_LENGTH = 100;

    private ChapterMarks() {
    }

    public static void validate(List<ChapterMark> marks) {
        if (marks.isEmpty()) {
            return;
        }
        if (marks.get(0).seconds() != 0) {
            throw invalid("The first chapter must start at 00:00.");
        }
        for (int index = 0; index < marks.size(); index++) {
            ChapterMark mark = marks.get(index);
            if (mark.title() == null || mark.title().isBlank() || mark.title().length() > MAX_TITLE_LENGTH) {
                throw invalid("Every chapter needs a title of 1 to " + MAX_TITLE_LENGTH + " characters.");
            }
            if (index > 0 && mark.seconds() - marks.get(index - 1).seconds() < MIN_GAP_SECONDS) {
                throw invalid("Chapters must be in ascending order and at least " + MIN_GAP_SECONDS
                        + " seconds apart; chapter " + (index + 1) + " is not.");
            }
        }
    }

    private static ProblemException invalid(String detail) {
        return new ProblemException(400, "Invalid chapter marks", detail);
    }
}
