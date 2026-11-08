package net.onelitefeather.menura.core.brand;

import java.time.Instant;

/**
 * Where the brand kit reports that a version was published. The server delivers the notification over its event stream;
 * a client that missed it reads the current version after it reconnects (principle 3).
 */
public interface BrandKitEvents {

    /** One version was published and committed. */
    void published(KitPublished event);

    /** A publication notification: the version number only, the clients read the kit they need. */
    record KitPublished(int version, Instant publishedAt) {
    }
}
