package net.onelitefeather.menura.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Long-lived secrets: 256 random bits from a cryptographically secure source, handed out once in URL-safe form.
 * Only the SHA-256 digest is stored, and a presented secret is compared with {@link MessageDigest#isEqual}, which
 * takes the same time whatever the mismatch.
 */
public final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Secrets() {
    }

    /** A new secret: 32 random bytes, base64url without padding (43 characters). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** The stored form of a secret: its SHA-256 digest. */
    public static byte[] hash(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime provides SHA-256", e);
        }
    }

    /** Whether a presented secret belongs to a stored digest. */
    public static boolean matches(String presented, byte[] storedDigest) {
        return MessageDigest.isEqual(hash(presented), storedDigest);
    }
}
