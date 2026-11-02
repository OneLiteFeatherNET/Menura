package net.onelitefeather.menura.core.secret;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretGeneratorTest {

    @Test
    void secretIsUrlSafeBase64WithoutPaddingOf256Bits() {
        String secret = new SecretGenerator(new SequenceRandom()).next();

        assertTrue(secret.matches("[A-Za-z0-9_-]{43}"),
                "a 256-bit secret is 43 URL-safe base64 characters without padding, but was " + secret);
        assertEquals(32, Base64.getUrlDecoder().decode(secret).length, "the secret must decode to 32 bytes");
    }

    @Test
    void secretDrawsItsBytesFromTheInjectedSecureRandom() {
        FillRandom random = new FillRandom((byte) 0x5A);

        String secret = new SecretGenerator(random).next();

        assertEquals(1, random.calls(), "the generator must draw from the injected source exactly once per secret");
        byte[] expected = new byte[32];
        Arrays.fill(expected, (byte) 0x5A);
        assertArrayEquals(expected, Base64.getUrlDecoder().decode(secret),
                "the secret must be exactly the bytes the injected source produced");
    }

    @Test
    void consecutiveSecretsDiffer() {
        SecretGenerator generator = new SecretGenerator(new SequenceRandom());

        assertNotEquals(generator.next(), generator.next(), "two secrets from one generator must differ");
    }

    /** Fills every requested byte with one value and counts the calls. */
    static final class FillRandom extends SecureRandom {

        private final byte fill;
        private int calls;

        FillRandom(byte fill) {
            this.fill = fill;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            calls++;
            Arrays.fill(bytes, fill);
        }

        int calls() {
            return calls;
        }
    }

    /** Produces a different byte pattern on every call, so consecutive secrets differ without real randomness. */
    static final class SequenceRandom extends SecureRandom {

        private int next;

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (next + i);
            }
            next++;
        }
    }
}
