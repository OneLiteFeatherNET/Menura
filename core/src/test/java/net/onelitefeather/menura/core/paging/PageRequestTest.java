package net.onelitefeather.menura.core.paging;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestTest {

    @Test
    void limitDefaultsTo50WhenAbsent() {
        PageRequest request = PageRequest.of(null, null);

        assertEquals(50, request.limit());
        assertNull(request.afterKey(), "a request without a cursor starts at the first page");
    }

    @Test
    void limitIsClampedToMaximum200() {
        assertEquals(200, PageRequest.of(500, null).limit());
    }

    @Test
    void limitBelowOneIsRefusedAsProblemDetails() {
        ProblemException failure = assertThrows(ProblemException.class, () -> PageRequest.of(0, null));

        assertEquals(400, failure.status(), "a limit below one is a client error");
    }

    @Test
    void cursorRoundTripsTheSortKey() {
        String cursor = Cursor.encode("2026-01-01T00:00:00Z|0190");

        assertEquals("2026-01-01T00:00:00Z|0190", PageRequest.of(10, cursor).afterKey());
    }

    @Test
    void invalidCursorIsRefusedAsProblemDetails() {
        ProblemException failure = assertThrows(ProblemException.class,
                () -> PageRequest.of(10, "not a cursor!!"));

        assertEquals(400, failure.status(), "an invalid cursor is a client error");
        assertEquals("Invalid cursor", failure.title());
    }

    @Test
    void pageEnvelopeCarriesItemsAndNextCursor() {
        Page<String> page = new Page<>(java.util.List.of("a", "b"), null);

        assertEquals(java.util.List.of("a", "b"), page.items());
        assertNull(page.nextCursor(), "the last page has no next cursor");
    }
}
