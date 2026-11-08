package net.onelitefeather.menura.core.secret;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII));
    private static final byte[] AAD = "channel-1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAIN = "ya29.access-token".getBytes(StandardCharsets.UTF_8);

    @Test
    void encryptedTokenRoundTripsWithTheSameKey() {
        TokenCipher cipher = TokenCipher.of(KEY, null, new SecureRandom());

        assertArrayEquals(PLAIN, cipher.decrypt(cipher.encrypt(PLAIN, AAD), AAD),
                "the decrypted token equals the original");
    }

    @Test
    void ciphertextDoesNotContainThePlainToken() {
        byte[] blob = TokenCipher.of(KEY, null, new SecureRandom()).encrypt(PLAIN, AAD);

        assertFalse(new String(blob, StandardCharsets.ISO_8859_1).contains("ya29"),
                "a database dump must not reveal the token");
    }

    @Test
    void ciphertextBoundToADifferentRowIsRefused() {
        TokenCipher cipher = TokenCipher.of(KEY, null, new SecureRandom());
        byte[] blob = cipher.encrypt(PLAIN, AAD);

        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(blob, "channel-2".getBytes(StandardCharsets.UTF_8)),
                "a ciphertext moved to another channel must not decrypt");
    }

    @Test
    void previousKeyStillDecryptsGrantsWrittenBeforeRotation() {
        byte[] old = TokenCipher.of(OTHER_KEY, null, new SecureRandom()).encrypt(PLAIN, AAD);
        TokenCipher rotated = TokenCipher.of(KEY, OTHER_KEY, new SecureRandom());

        assertArrayEquals(PLAIN, rotated.decrypt(old, AAD),
                "the previous key still reads grants written before rotation");
    }

    @Test
    void missingKeyStopsWithAClearMessageThatNamesNoKeyValue() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> TokenCipher.of("", null, new SecureRandom()));

        assertTrue(failure.getMessage().contains("MENURA_TOKEN_ENCRYPTION_KEY"),
                "the message names the variable the operator must set");
    }

    @Test
    void keyOfTheWrongLengthIsRefused() {
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalArgumentException.class, () -> TokenCipher.of(short16, null, new SecureRandom()),
                "AES-256 needs a 32-byte key");
    }
}
