package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.Channel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each test builds its own repository; the prototype ignores the connection argument, so it is passed as null. */
class PrototypeAuditRepositoryTest {

    private static AuditEntry entry(int number) {
        return new AuditEntry(UUID.nameUUIDFromBytes(("entry-" + number).getBytes()), null, "test.write",
                "target-" + number, Instant.parse("2026-11-07T12:00:00Z"), Channel.WEB, "request-" + number);
    }

    @Test
    void theRepositoryKeepsAtMostOneThousandEntries() {
        PrototypeAuditRepository repository = new PrototypeAuditRepository();

        for (int number = 1; number <= 1_001; number++) {
            repository.insert(null, entry(number));
        }

        assertEquals(1_000, repository.entries().size());
    }

    @Test
    void theFirstInsertBeyondTheCapacityDropsTheOldestEntry() {
        PrototypeAuditRepository repository = new PrototypeAuditRepository();
        for (int number = 1; number <= 1_000; number++) {
            repository.insert(null, entry(number));
        }

        repository.insert(null, entry(1_001));

        List<AuditEntry> kept = repository.entries();
        assertEquals(entry(2), kept.get(0), "the first entry was dropped, the second is now the oldest");
        assertEquals(entry(1_001), kept.get(kept.size() - 1), "the newest entry is kept last");
    }

    @Test
    void entriesAreReturnedInTheOrderTheyWereWritten() {
        PrototypeAuditRepository repository = new PrototypeAuditRepository();
        repository.insert(null, entry(1));
        repository.insert(null, entry(2));

        assertEquals(List.of(entry(1), entry(2)), repository.entries());
    }
}
