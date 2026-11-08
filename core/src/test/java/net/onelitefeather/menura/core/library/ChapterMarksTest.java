package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChapterMarksTest {

    @Test
    void ascendingMarksStartingAtZeroAndTenSecondsApartAreAccepted() {
        List<ChapterMark> marks = List.of(new ChapterMark(0, "Intro"), new ChapterMark(10, "Game"),
                new ChapterMark(70, "Outro"));

        assertDoesNotThrow(() -> ChapterMarks.validate(marks));
    }

    @Test
    void firstMarkNotAtZeroIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> ChapterMarks.validate(List.of(new ChapterMark(5, "Intro"))));

        assertEquals("Invalid chapter marks", refused.title(), "the problem names the chapter rule");
    }

    @Test
    void marksNotInAscendingOrderAreRefused() {
        assertThrows(ProblemException.class,
                () -> ChapterMarks.validate(List.of(new ChapterMark(0, "A"), new ChapterMark(60, "B"),
                        new ChapterMark(30, "C"))));
    }

    @Test
    void marksLessThanTenSecondsApartAreRefused() {
        assertThrows(ProblemException.class,
                () -> ChapterMarks.validate(List.of(new ChapterMark(0, "A"), new ChapterMark(9, "B"))));
    }

    @Test
    void noMarksAreAccepted() {
        assertDoesNotThrow(() -> ChapterMarks.validate(List.of()));
    }
}
