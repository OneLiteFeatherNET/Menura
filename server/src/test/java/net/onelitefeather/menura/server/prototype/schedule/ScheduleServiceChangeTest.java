package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The rules of PUT and DELETE /api/prototype/publications/{id}. Each test builds its own harness. */
class ScheduleServiceChangeTest {

    private static final Instant MOVED = Instant.parse("2026-11-24T17:00:00Z");

    @Test
    void aPlannedPublicationCanBeMoved() {
        ScheduleHarness h = ScheduleHarness.seeded();
        String id = h.idOf("a-104", "tt-de");

        Publication moved = h.service.update(id, new ScheduleService.UpdateRequest(MOVED, null));

        assertEquals(MOVED, moved.publishAt(), "the new time is stored");
        assertEquals(MOVED, h.repository.find(id).orElseThrow().publishAt(), "and kept in the store");
    }

    @Test
    void aScheduledPublicationCannotBeMovedAndIsRefusedWith409() {
        ScheduleHarness h = ScheduleHarness.seeded();
        String id = h.idOf("a-104", "yt-de");

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.update(id, new ScheduleService.UpdateRequest(MOVED, null)));

        assertEquals(409, refused.status());
    }

    @Test
    void aPublishedPostCannotBeChangedAndIsRefusedWith409() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.update("pub-p-41", new ScheduleService.UpdateRequest(MOVED, null)));

        assertEquals(409, refused.status());
    }

    @Test
    void movingAPlannedPublicationIntoThePastIsRefusedWith422() {
        ScheduleHarness h = ScheduleHarness.seeded();
        String id = h.idOf("a-104", "tt-de");

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.update(id, new ScheduleService.UpdateRequest(ScheduleHarness.NOW, null)));

        assertEquals(422, refused.status());
    }

    @Test
    void anUnknownPublicationIsNotFound() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.service.update("pub-999", new ScheduleService.UpdateRequest(MOVED, null)));

        assertEquals(404, refused.status());
    }

    @Test
    void aPlannedPublicationCanBeDeleted() {
        ScheduleHarness h = ScheduleHarness.seeded();
        String id = h.idOf("a-104", "tt-de");

        h.service.delete(id);

        assertFalse(h.repository.find(id).isPresent(), "the publication is gone");
    }

    @Test
    void aScheduledPublicationCanBeDeleted() {
        ScheduleHarness h = ScheduleHarness.seeded();
        String id = h.idOf("a-104", "yt-de");

        h.service.delete(id);

        assertFalse(h.repository.find(id).isPresent(), "a native schedule is withdrawn with the publication");
    }

    @Test
    void aFailedPublicationCannotBeDeletedAndIsRefusedWith409() {
        ScheduleHarness h = ScheduleHarness.seeded();
        String id = h.idOf("a-105", "tt-de");

        ProblemException refused = assertThrows(ProblemException.class, () -> h.service.delete(id));

        assertEquals(409, refused.status());
    }

    @Test
    void aPublishedPostCannotBeDeletedAndIsRefusedWith409() {
        ScheduleHarness h = ScheduleHarness.seeded();

        ProblemException refused = assertThrows(ProblemException.class, () -> h.service.delete("pub-p-41"));

        assertEquals(409, refused.status());
    }
}
