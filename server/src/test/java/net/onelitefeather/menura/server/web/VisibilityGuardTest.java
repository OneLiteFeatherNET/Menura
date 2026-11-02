package net.onelitefeather.menura.server.web;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisibilityGuardTest {

    private static final UUID ID = UUID.fromString("0b0c2f7e-7d4a-4c2b-9d1e-3f5a6b7c8d9e");
    private static final UUID UNKNOWN = UUID.fromString("11111111-2222-4333-8444-555555555555");

    private static final ObjectAccess<String> VISIBLE_AND_READABLE = access(true, true);
    private static final ObjectAccess<String> HIDDEN = access(false, true);
    private static final ObjectAccess<String> VISIBLE_WITHOUT_RIGHT = access(true, false);

    @Test
    void visibleObjectWithTheRightIsReturned() {
        VisibilityGuard<UUID, String> guard = guardWith(ID, "recording");

        String object = guard.read(ID, VISIBLE_AND_READABLE);

        assertEquals("recording", object);
    }

    @Test
    void hiddenObjectAnswers404AsProblemDetails() {
        VisibilityGuard<UUID, String> guard = guardWith(ID, "recording");

        ProblemException failure = assertThrows(ProblemException.class, () -> guard.read(ID, HIDDEN));

        assertEquals(404, failure.status(), "an object the caller may not see must answer 404");
        assertEquals("Not Found", failure.title());
    }

    @Test
    void visibleObjectWithoutTheRightAnswers403AsProblemDetails() {
        VisibilityGuard<UUID, String> guard = guardWith(ID, "recording");

        ProblemException failure = assertThrows(ProblemException.class, () -> guard.read(ID, VISIBLE_WITHOUT_RIGHT));

        assertEquals(403, failure.status(), "a visible object without the required right must answer 403");
        assertEquals("Forbidden", failure.title());
    }

    @Test
    void hiddenAndMissingObjectsAnswerTheSameProblem() {
        VisibilityGuard<UUID, String> guard = guardWith(ID, "recording");

        ProblemException hidden = assertThrows(ProblemException.class, () -> guard.read(ID, HIDDEN));
        ProblemException missing = assertThrows(ProblemException.class, () -> guard.read(UNKNOWN, HIDDEN));

        assertEquals(missing.status(), hidden.status(), "hidden and missing must not differ in status");
        assertEquals(missing.title(), hidden.title(), "hidden and missing must not differ in title");
        assertEquals(missing.detail(), hidden.detail(), "hidden and missing must not differ in detail");
    }

    @Test
    void missingObjectAnswers404() {
        VisibilityGuard<UUID, String> guard = new VisibilityGuard<>(new FakeObjectRepository<>(), "recording");

        ProblemException failure = assertThrows(ProblemException.class, () -> guard.read(ID, VISIBLE_AND_READABLE));

        assertEquals(404, failure.status());
    }

    @Test
    void rightIsNotConsultedForAHiddenObject() {
        boolean[] rightChecked = {false};
        ObjectAccess<String> spy = new ObjectAccess<>() {
            @Override
            public boolean isVisible(String object) {
                return false;
            }

            @Override
            public boolean mayRead(String object) {
                rightChecked[0] = true;
                return true;
            }
        };
        VisibilityGuard<UUID, String> guard = guardWith(ID, "recording");

        assertThrows(ProblemException.class, () -> guard.read(ID, spy));

        assertFalse(rightChecked[0], "the right must not be evaluated before visibility, or the answer leaks existence");
    }

    @Test
    void oneReadLooksTheObjectUpExactlyOnce() {
        FakeObjectRepository<UUID, String> repository = new FakeObjectRepository<UUID, String>().with(ID, "recording");
        VisibilityGuard<UUID, String> guard = new VisibilityGuard<>(repository, "recording");

        guard.read(ID, VISIBLE_AND_READABLE);

        assertEquals(1, repository.lookups(), "one read must look the object up exactly once");
    }

    private static VisibilityGuard<UUID, String> guardWith(UUID id, String object) {
        return new VisibilityGuard<>(new FakeObjectRepository<UUID, String>().with(id, object), "recording");
    }

    private static ObjectAccess<String> access(boolean visible, boolean mayRead) {
        return new ObjectAccess<>() {
            @Override
            public boolean isVisible(String object) {
                return visible;
            }

            @Override
            public boolean mayRead(String object) {
                return mayRead;
            }
        };
    }
}
