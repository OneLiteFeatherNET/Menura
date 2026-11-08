package net.onelitefeather.menura.server.brand;

import java.time.Instant;

/** The version a consumer has pinned, and when the pin was last set. */
public record KitPin(String consumer, int version, Instant updatedAt) {
}
