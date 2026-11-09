package net.onelitefeather.menura.core.attribution;

import java.time.Instant;
import java.util.UUID;

/** The source a player named for one first join, and when the hub accepted it. At most one per join. */
public record JoinAnswer(UUID eventId, StatedSource source, Instant answeredAt) {
}
