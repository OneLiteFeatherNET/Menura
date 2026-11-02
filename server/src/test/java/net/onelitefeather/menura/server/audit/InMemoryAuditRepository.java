package net.onelitefeather.menura.server.audit;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Keeps audit entries in memory and remembers the connection each one was written on. */
@Singleton
@Replaces(JdbcAuditRepository.class)
public class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    @Override
    public void insert(Connection connection, AuditEntry entry) {
        connections.add(connection);
        entries.add(entry);
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    public List<Connection> connections() {
        return List.copyOf(connections);
    }
}
