package net.onelitefeather.menura.core.secret;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Creates long-lived secrets: 256 random bits from an injected {@link SecureRandom}, encoded as URL-safe base64
 * without padding (43 characters). The plain value is shown to its owner once and is never stored; only its
 * {@link SecretHasher hash} is kept.
 */
public final class SecretGenerator {

    /** Length of a secret in bytes: 256 bits. */
    public static final int BYTES = 32;

    private final SecureRandom random;

    public SecretGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public String next() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
