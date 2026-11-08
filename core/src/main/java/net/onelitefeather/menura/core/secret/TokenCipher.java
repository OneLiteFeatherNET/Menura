package net.onelitefeather.menura.core.secret;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Encrypts platform tokens at rest with AES-256-GCM. The key comes from configuration as base64 of 32 bytes. A second
 * key may be given for decryption only, so a rotation can read grants written under the old key; every new write uses
 * the primary key.
 *
 * <p>Layout of a stored value: one version byte, a 12-byte random nonce, then the ciphertext with its 16-byte tag. The
 * additional authenticated data binds a value to its row, so a ciphertext moved to another channel does not decrypt.
 */
public final class TokenCipher {

    /** The environment variable that holds the primary key. Named in the error when it is missing. */
    public static final String KEY_VARIABLE = "MENURA_TOKEN_ENCRYPTION_KEY";

    private static final int VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec primary;
    private final SecretKeySpec previous;
    private final SecureRandom random;

    private TokenCipher(SecretKeySpec primary, SecretKeySpec previous, SecureRandom random) {
        this.primary = primary;
        this.previous = previous;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Builds the cipher from base64 keys. A blank primary key is refused with a message that names the variable, so
     * the caller can stop the process at start.
     *
     * @param primaryKey  base64 of 32 bytes; used for every write and tried first on read
     * @param previousKey base64 of 32 bytes or null; tried on read only
     */
    public static TokenCipher of(String primaryKey, String previousKey, SecureRandom random) {
        if (primaryKey == null || primaryKey.isBlank()) {
            throw new IllegalStateException(KEY_VARIABLE + " is not set. Generate a key with `openssl rand -base64 32` "
                    + "and provide it through the environment or the Kubernetes secret; the server does not start without it.");
        }
        SecretKeySpec previous = previousKey == null || previousKey.isBlank() ? null : key(previousKey);
        return new TokenCipher(key(primaryKey), previous, Objects.requireNonNull(random, "random"));
    }

    /** Encrypts a token for one row. The result is the only form that reaches the database. */
    public byte[] encrypt(byte[] plaintext, byte[] associatedData) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, primary, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData);
            byte[] body = cipher.doFinal(plaintext);
            byte[] out = new byte[1 + NONCE_BYTES + body.length];
            out[0] = VERSION;
            System.arraycopy(nonce, 0, out, 1, NONCE_BYTES);
            System.arraycopy(body, 0, out, 1 + NONCE_BYTES, body.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("token encryption failed", e);
        }
    }

    /**
     * Decrypts a stored value for its row. Tries the primary key, then the previous one. A value that matches neither
     * key, belongs to another row or has an unknown version is refused; the message never names the value.
     */
    public byte[] decrypt(byte[] stored, byte[] associatedData) {
        if (stored == null || stored.length < 1 + NONCE_BYTES + TAG_BITS / 8 || stored[0] != VERSION) {
            throw new IllegalStateException("stored token has an unknown format");
        }
        byte[] nonce = Arrays.copyOfRange(stored, 1, 1 + NONCE_BYTES);
        byte[] body = Arrays.copyOfRange(stored, 1 + NONCE_BYTES, stored.length);
        try {
            return open(primary, nonce, body, associatedData);
        } catch (AEADBadTagException primaryFailed) {
            if (previous == null) {
                throw new IllegalStateException("stored token cannot be decrypted with the configured key");
            }
            try {
                return open(previous, nonce, body, associatedData);
            } catch (AEADBadTagException previousFailed) {
                throw new IllegalStateException("stored token cannot be decrypted with the configured keys");
            }
        }
    }

    private static byte[] open(SecretKeySpec key, byte[] nonce, byte[] body, byte[] associatedData)
            throws AEADBadTagException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(body);
        } catch (AEADBadTagException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("token decryption failed", e);
        }
    }

    private static SecretKeySpec key(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(KEY_VARIABLE + " is not valid base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(KEY_VARIABLE + " must decode to 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
