package net.onelitefeather.menura.server.brand;

import net.onelitefeather.menura.core.brand.BrandKit;

import java.time.Instant;
import java.util.UUID;

/** One published version of the brand kit, as stored: its number, the kit and who published it when. */
public record StoredKitVersion(int version, BrandKit kit, UUID publishedBy, Instant createdAt) {
}
