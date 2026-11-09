package net.onelitefeather.menura.core.attribution;

import java.time.Instant;
import java.util.UUID;

/**
 * A first join with its stated source, or {@code null} when the player did not answer. Only the fields the attribution
 * and the reports need.
 */
public record FirstJoin(UUID eventId, Instant joinedAt, StatedSource source) {
}
