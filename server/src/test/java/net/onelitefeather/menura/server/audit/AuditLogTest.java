package net.onelitefeather.menura.server.audit;

import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.db.DirectTransactions;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.support.DeterministicRandom;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogTest {

    private static final UUID PERSON = UUID.fromString("0b6f8f3e-2c1a-4d5e-9f70-1a2b3c4d5e6f");
    private static final Caller CALLER = new Caller(PERSON, Channel.OBS);
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final DirectTransactions transactions = new DirectTransactions();
    private final InMemoryAuditRepository repository = new InMemoryAuditRepository();

    private AuditLog auditLog(Instant at) {
        Clock clock = Clock.fixed(at, ZoneOffset.UTC);
        return new AuditLog(transactions, clock, new IdentifierGenerator(new DeterministicRandom()), repository);
    }

    @Test
    void writeRecordsOneEntryWithPersonActionTargetChannelAndRequestId() throws SQLException {
        auditLog(NOW).write(CALLER, "recording.create", "recording:7", "req-42", connection -> "done");

        assertEquals(1, repository.entries().size(), "one write must produce exactly one audit entry");
        AuditEntry entry = repository.entries().get(0);
        assertEquals(PERSON, entry.personId(), "the entry must name the acting person");
        assertEquals("recording.create", entry.action(), "the entry must name the action");
        assertEquals("recording:7", entry.target(), "the entry must name the target");
        assertEquals(Channel.OBS, entry.channel(), "the entry must name the channel the write came from");
        assertEquals("req-42", entry.requestId(), "the entry must carry the request id");
    }

    @Test
    void entryTimeIsTakenFromTheInjectedClock() throws SQLException {
        Instant fixed = Instant.parse("2030-01-01T00:00:00Z");

        auditLog(fixed).write(CALLER, "recording.create", "recording:7", "req-42", connection -> null);

        assertEquals(fixed, repository.entries().get(0).occurredAt(), "the time must come from the injected clock");
    }

    @Test
    void writeReturnsTheResultOfTheWork() throws SQLException {
        String result = auditLog(NOW).write(CALLER, "recording.create", "recording:7", "req-42", connection -> "done");

        assertEquals("done", result);
    }

    @Test
    void entryIsWrittenOnTheConnectionOfTheWriteInOneTransaction() throws SQLException {
        AtomicReference<Connection> writeConnection = new AtomicReference<>();

        auditLog(NOW).write(CALLER, "recording.create", "recording:7", "req-42", connection -> {
            writeConnection.set(connection);
            return null;
        });

        assertEquals(1, transactions.connections().size(), "the write and its entry must share one transaction");
        assertSame(writeConnection.get(), repository.connections().get(0),
                "the audit entry must be written on the connection of the write");
    }

    @Test
    void failedWriteRecordsNoEntry() {
        assertThrows(IllegalStateException.class, () -> auditLog(NOW).write(CALLER, "recording.create",
                "recording:7", "req-42", connection -> {
                    throw new IllegalStateException("write failed");
                }));

        assertTrue(repository.entries().isEmpty(), "a write that fails must leave no audit entry");
    }
}
