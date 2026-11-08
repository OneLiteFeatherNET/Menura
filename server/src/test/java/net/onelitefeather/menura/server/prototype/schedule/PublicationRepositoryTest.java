package net.onelitefeather.menura.server.prototype.schedule;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The capacity of the publication store. Each test builds its own repository. */
class PublicationRepositoryTest {

    @Test
    void aBatchThatFillsTheStoreExactlyIsAccepted() {
        ScheduleHarness h = ScheduleHarness.seeded();
        int room = PublicationRepository.CAPACITY - h.repository.size();

        h.repository.insertAll(IntStream.range(0, room).mapToObj(i -> ScheduleHarness.row("fill-" + i)).toList());

        assertEquals(PublicationRepository.CAPACITY, h.repository.size(), "the store is exactly full");
    }

    @Test
    void aBatchThatWouldExceedTheCapacityIsRefusedAndStoresNothing() {
        ScheduleHarness h = ScheduleHarness.seeded();
        int room = PublicationRepository.CAPACITY - h.repository.size();
        h.repository.insertAll(IntStream.range(0, room).mapToObj(i -> ScheduleHarness.row("fill-" + i)).toList());

        ProblemException refused = assertThrows(ProblemException.class,
                () -> h.repository.insertAll(List.of(ScheduleHarness.row("one-more"))));

        assertEquals(422, refused.status());
        assertEquals(PublicationRepository.CAPACITY, h.repository.size(), "nothing was stored");
    }

    @Test
    void theNextIdDoesNotCollideWithASeededId() {
        ScheduleHarness h = ScheduleHarness.seeded();

        String next = h.repository.nextId();

        assertFalse(h.repository.find(next).isPresent(), "a fresh id is not stored yet");
    }
}
