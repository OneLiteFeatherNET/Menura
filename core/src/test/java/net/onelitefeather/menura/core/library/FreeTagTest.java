package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FreeTagTest {

    @Test
    void lowerCaseTagWithDigitsAndHyphensIsAccepted() {
        assertEquals("speed-run-2", FreeTag.parse("speed-run-2"), "a matching tag is stored as given");
    }

    @Test
    void tagWithUpperCaseOrSpaceIsRefused() {
        assertThrows(ProblemException.class, () -> FreeTag.parse("Speed Run"),
                "an upper-case letter or a space breaks the pattern");
    }

    @Test
    void tagShorterThanTwoCharactersIsRefused() {
        assertThrows(ProblemException.class, () -> FreeTag.parse("a"), "one character is below the minimum length");
    }

    @Test
    void tagLongerThanThirtyCharactersIsRefused() {
        assertThrows(ProblemException.class, () -> FreeTag.parse("a".repeat(31)), "31 characters exceed the maximum");
    }

    @Test
    void twentyTagsPerItemAreAccepted() {
        Set<String> tags = IntStream.range(0, 20).mapToObj(i -> "tag-" + i).collect(java.util.stream.Collectors.toSet());

        FreeTag.requireWithinLimit(tags);
    }

    @Test
    void twentyOneTagsPerItemAreRefused() {
        Set<String> tags = IntStream.range(0, 21).mapToObj(i -> "tag-" + i).collect(java.util.stream.Collectors.toSet());

        assertThrows(ProblemException.class, () -> FreeTag.requireWithinLimit(tags), "an item holds at most 20 tags");
    }
}
