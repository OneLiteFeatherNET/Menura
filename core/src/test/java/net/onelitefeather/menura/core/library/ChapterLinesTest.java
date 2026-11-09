package net.onelitefeather.menura.core.library;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterLinesTest {

    @Test
    void aTimestampUnderAnHourIsMinutesAndSecondsWithPaddedFields() {
        assertEquals("00:00", ChapterLines.timestamp(0), "the start is 00:00");
        assertEquals("01:30", ChapterLines.timestamp(90), "one and a half minutes");
        assertEquals("59:59", ChapterLines.timestamp(3599), "the last second before an hour");
    }

    @Test
    void aTimestampFromAnHourIsHoursMinutesAndSeconds() {
        assertEquals("1:00:00", ChapterLines.timestamp(3600), "exactly one hour");
        assertEquals("1:02:03", ChapterLines.timestamp(3723), "an hour, two minutes and three seconds");
        assertEquals("10:00:00", ChapterLines.timestamp(36_000), "ten hours");
    }

    @Test
    void aNegativeTimestampIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ChapterLines.timestamp(-1),
                "a chapter cannot start before the beginning");
    }

    @Test
    void eachMarkIsOneLineWithItsTimestampAndTitle() {
        List<String> lines = ChapterLines.lines(List.of(new ChapterMark(0, "Intro"), new ChapterMark(90, "Setup"),
                new ChapterMark(300, "Demo")));

        assertEquals(List.of("00:00 Intro", "01:30 Setup", "05:00 Demo"), lines, "one line per mark, in ascending order");
    }

    @Test
    void theLinesAreInAscendingOrderEvenWhenTheMarksArriveUnsorted() {
        List<String> lines = ChapterLines.lines(List.of(new ChapterMark(300, "Demo"), new ChapterMark(0, "Intro")));

        assertEquals(List.of("00:00 Intro", "05:00 Demo"), lines, "the chapters are listed in playback order");
    }

    @Test
    void lineBreaksInATitleCannotStartANewLine() {
        List<String> lines = ChapterLines.lines(List.of(new ChapterMark(60, "Part\n two")));

        assertEquals(List.of("01:00 Part two"), lines, "a title stays on the line of its timestamp");
    }

    @Test
    void noMarksGiveNoLines() {
        assertTrue(ChapterLines.lines(List.of()).isEmpty(), "a timeline without marks adds no lines");
    }

    @Test
    void theLinesFollowTheDescriptionAfterABlankLine() {
        String description = ChapterLines.appendTo("Text", List.of(new ChapterMark(0, "Intro"), new ChapterMark(90, "Setup")));

        assertEquals("Text\n\n00:00 Intro\n01:30 Setup", description, "the description keeps its text and gets the chapters");
    }

    @Test
    void aDescriptionWithoutMarksIsUnchanged() {
        assertEquals("Text", ChapterLines.appendTo("Text", List.of()), "a post without chapters keeps its description");
    }

    @Test
    void anEmptyDescriptionGetsOnlyTheLines() {
        String description = ChapterLines.appendTo("", List.of(new ChapterMark(0, "Intro")));

        assertEquals("00:00 Intro", description, "no blank lines are added before the chapters");
    }
}
