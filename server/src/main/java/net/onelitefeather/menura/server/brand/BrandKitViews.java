package net.onelitefeather.menura.server.brand;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.brand.BrandKit;

import java.time.Instant;
import java.util.Optional;
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

    /**
     * The render bundle: the version and its whole kit, and the installation join domain that the short's call to action
     * takes as its join_domain parameter. The domain is the same for every channel; it is null when none is configured.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RenderBundle(int version, UUID publishedBy, Instant createdAt, BrandKit kit, String joinDomain) {

        static RenderBundle of(StoredKitVersion stored, Optional<String> joinDomain) {
            String domain = joinDomain.filter(value -> !value.isBlank()).orElse(null);
            return new RenderBundle(stored.version(), stored.publishedBy(), stored.createdAt(), stored.kit(), domain);
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
