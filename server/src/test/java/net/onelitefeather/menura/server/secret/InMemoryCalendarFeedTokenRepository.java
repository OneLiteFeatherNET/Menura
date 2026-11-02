package net.onelitefeather.menura.server.secret;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Keeps feed token rows in memory and remembers the connection each write used. */
@Singleton
@Replaces(JdbcCalendarFeedTokenRepository.class)
public class InMemoryCalendarFeedTokenRepository implements CalendarFeedTokenRepository {

    private final List<FeedToken> rows = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    @Override
    public Optional<FeedToken> findActive(Connection connection, UUID id) {
        return rows.stream().filter(row -> row.id().equals(id) && row.revokedAt() == null).findFirst();
    }

    @Override
    public void revokeActive(Connection connection, UUID personId, Instant revokedAt) {
        connections.add(connection);
        for (int i = 0; i < rows.size(); i++) {
            FeedToken row = rows.get(i);
            if (row.personId().equals(personId) && row.revokedAt() == null) {
                rows.set(i, row.revokedAt(revokedAt));
            }
        }
    }

    @Override
    public void insert(Connection connection, FeedToken token) {
        connections.add(connection);
        rows.add(token);
    }

    public List<FeedToken> rows() {
        return List.copyOf(rows);
    }

    public List<Connection> connections() {
        return List.copyOf(connections);
    }
}
