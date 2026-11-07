package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.AuditRepository;
import net.onelitefeather.menura.server.audit.JdbcAuditRepository;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps the audit entries of the prototype in memory, in the order they were written. */
@Singleton
@Replaces(JdbcAuditRepository.class)
@Requires(env = "prototype")
public class PrototypeAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void insert(Connection connection, AuditEntry entry) {
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}
