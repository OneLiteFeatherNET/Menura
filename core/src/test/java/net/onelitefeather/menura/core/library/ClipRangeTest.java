package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClipRangeTest {

    @Test
    void clipWithinTheRecordingIsAccepted() {
        ClipRange range = ClipRange.of(120, 180).within(3600);

        assertEquals(120, range.startSeconds(), "the start is kept as given");
        assertEquals(180, range.endSeconds(), "the end is kept as given");
    }

    @Test
    void clipEndingAfterTheRecordingIsRefused() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> ClipRange.of(3500, 3700).within(3600));

        assertEquals(400, refused.status(), "a clip outside its recording is a validation error");
    }

    @Test
    void emptyClipIsRefused() {
        assertThrows(ProblemException.class, () -> ClipRange.of(60, 60), "an empty clip is refused");
    }

    @Test
    void negativeStartIsRefused() {
        assertThrows(ProblemException.class, () -> ClipRange.of(-1, 10), "a clip cannot start before the recording");
    }
}
