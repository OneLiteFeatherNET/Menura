package net.onelitefeather.menura.core.channel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Proof Key for Code Exchange (RFC 7636) with the S256 method, for the consent redirects to platforms. */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    /** A new code verifier: 32 random bytes, which gives the 43 characters RFC 7636 requires at least. */
    public static String newVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** The S256 challenge of a verifier: base64url of its SHA-256 digest. */
    public static String s256Challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
