package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.secret.CalendarFeedTokenRepository;
import net.onelitefeather.menura.server.secret.FeedToken;
import net.onelitefeather.menura.server.secret.JdbcCalendarFeedTokenRepository;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Calendar feed token rows of the prototype, kept in memory. Active only in the prototype environment. */
@Singleton
@Replaces(JdbcCalendarFeedTokenRepository.class)
@Requires(env = "prototype")
public class PrototypeCalendarFeedTokenRepository implements CalendarFeedTokenRepository {

    private final List<FeedToken> rows = new ArrayList<>();

    @Override
    public synchronized Optional<FeedToken> findActive(Connection connection, UUID id) {
        return rows.stream().filter(row -> row.id().equals(id) && row.revokedAt() == null).findFirst();
    }

    @Override
    public synchronized void revokeActive(Connection connection, UUID personId, Instant revokedAt) {
        for (int i = 0; i < rows.size(); i++) {
            FeedToken row = rows.get(i);
            if (row.personId().equals(personId) && row.revokedAt() == null) {
                rows.set(i, row.revokedAt(revokedAt));
            }
        }
    }

    @Override
    public synchronized void insert(Connection connection, FeedToken token) {
        rows.add(token);
    }
}
