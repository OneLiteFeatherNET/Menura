package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.AuditRepository;
import net.onelitefeather.menura.server.audit.JdbcAuditRepository;

import java.sql.Connection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the most recent audit entries of the prototype in memory, in the order they were written. The log is a ring of
 * {@link #CAPACITY} entries: an insert beyond the capacity drops the oldest entry, so the memory of a long-running
 * prototype stays bounded. All access holds the lock of this repository.
 */
@Singleton
@Replaces(JdbcAuditRepository.class)
@Requires(env = "prototype")
public class PrototypeAuditRepository implements AuditRepository {

    static final int CAPACITY = 1_000;

    private final Deque<AuditEntry> entries = new ArrayDeque<>(CAPACITY);

    @Override
    public synchronized void insert(Connection connection, AuditEntry entry) {
        if (entries.size() == CAPACITY) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    public synchronized List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}
