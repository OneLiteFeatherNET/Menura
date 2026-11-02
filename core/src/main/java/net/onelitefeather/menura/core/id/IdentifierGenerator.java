package net.onelitefeather.menura.core.id;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates the identifiers of every entity: random RFC 9562 version 4 UUIDs. They are drawn from an injected
 * secure random source, and carry no time or sequence information, so they reveal neither creation time nor
 * the number of objects.
 */
public final class IdentifierGenerator {

    private static final int BYTES = 16;

    private final SecureRandom random;

    public IdentifierGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public UUID next() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
