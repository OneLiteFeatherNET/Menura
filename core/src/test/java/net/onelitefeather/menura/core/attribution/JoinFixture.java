package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.attribution.support.InMemoryJoinStore;
import net.onelitefeather.menura.core.channel.support.DirectTransactions;
import net.onelitefeather.menura.core.channel.support.MutableClock;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** One fresh world per test: an injected clock, an in-memory store and the service over them. */
final class JoinFixture {

    static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    static final String PSEUDONYM = "a".repeat(64);
    static final String OTHER_PSEUDONYM = "b".repeat(64);

    final MutableClock clock = new MutableClock(NOW);
    final InMemoryJoinStore store = new InMemoryJoinStore();
    final JoinService service = new JoinService(new DirectTransactions(), clock, ZONE, store);

    /** An event payload for a first join at the given time, with a fixed pseudonym. */
    static JoinPayloads.EventInput firstJoin(UUID eventId, Instant joinedAt) {
        return new JoinPayloads.EventInput(eventId, true, joinedAt, PSEUDONYM);
    }

    static JoinPayloads.EventInput eventOf(UUID eventId, boolean firstJoin, Instant joinedAt, String pseudonym) {
        return new JoinPayloads.EventInput(eventId, firstJoin, joinedAt, pseudonym);
    }

    /** A body as the proxy sends it, for the payload checks. */
    static Map<String, Object> body(String eventId, boolean firstJoin, String joinedAt, String pseudonym) {
        return Map.of("eventId", eventId, "firstJoin", firstJoin, "joinedAt", joinedAt, "pseudonym", pseudonym);
    }

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }
}
