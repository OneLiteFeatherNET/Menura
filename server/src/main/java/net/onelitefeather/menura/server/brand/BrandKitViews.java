package net.onelitefeather.menura.server.brand;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.brand.BrandKit;

import java.time.Instant;
import java.util.UUID;

/** The REST shapes of the brand kit. The kit itself is the core document, made serialisable by {@link BrandKitSerdes}. */
public final class BrandKitViews {

    private BrandKitViews() {
    }

    /** One published version: its number, who published it and when, and the whole kit. */
    @Serdeable
    public record Version(int version, UUID publishedBy, Instant createdAt, BrandKit kit) {

        static Version of(StoredKitVersion stored) {
            return new Version(stored.version(), stored.publishedBy(), stored.createdAt(), stored.kit());
        }
    }

    /** The version a consumer has pinned. */
    @Serdeable
    public record Pin(String consumer, int version, Instant updatedAt) {

        static Pin of(KitPin pin) {
            return new Pin(pin.consumer(), pin.version(), pin.updatedAt());
        }
    }

    /** The body that sets a pin: the number of the version to pin. */
    @Serdeable
    public record PinRequest(Integer version) {
    }
}
