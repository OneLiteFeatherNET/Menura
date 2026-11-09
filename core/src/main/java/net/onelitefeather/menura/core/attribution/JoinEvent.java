package net.onelitefeather.menura.core.attribution;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One join of a player as the network proxy reported it. It holds the proxy's event id, the first-join flag, the time of
 * the join, a pseudonym computed at the proxy with a keyed hash, and the time the hub received it. Nothing here identifies
 * the player.
 */
public record JoinEvent(UUID eventId, boolean firstJoin, Instant joinedAt, String pseudonym, Instant receivedAt) {

    public JoinEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(pseudonym, "pseudonym");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /** Whether a repeated delivery carries the same content as this event; the receipt time is not content. */
    public boolean sameContent(JoinEvent other) {
        return firstJoin == other.firstJoin && joinedAt.equals(other.joinedAt) && pseudonym.equals(other.pseudonym)
                && eventId.equals(other.eventId);
    }
}
