package net.onelitefeather.menura.core.secret;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stores and checks secrets as hashes. SHA-256 is the right function here, not a password hash: a secret is
 * 256 random bits, so guessing one is infeasible even without a salt or a slow function, and a fast digest keeps
 * every check cheap. A slow KDF would only pay off for low-entropy human passwords, which these are not.
 *
 * <p>Presented secrets are compared through {@link MessageDigest#isEqual}, which takes the same time whatever the
 * bytes are, so a caller cannot learn the stored hash one byte at a time.
 */
public final class SecretHasher {

    private static final String ALGORITHM = "SHA-256";

    private SecretHasher() {
    }

    /** The stored form of a secret: its 32-byte SHA-256 digest. */
    public static byte[] hash(String secret) {
        try {
            return MessageDigest.getInstance(ALGORITHM).digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is required by every Java runtime", e);
        }
    }

    /** Whether the presented secret is the one whose hash is stored. A damaged stored hash simply does not match. */
    public static boolean matches(String presented, byte[] storedHash) {
        return MessageDigest.isEqual(hash(presented), storedHash);
    }
}
