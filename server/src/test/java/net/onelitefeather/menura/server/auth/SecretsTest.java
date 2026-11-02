package net.onelitefeather.menura.server.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretsTest {

    @Test
    void generatedSecretCarries256Bits() {
        byte[] decoded = Base64.getUrlDecoder().decode(Secrets.generate());

        assertEquals(32, decoded.length, "a long-lived secret is 256 bits of randomness");
    }

    @Test
    void consecutiveSecretsDiffer() {
        assertNotEquals(Secrets.generate(), Secrets.generate());
    }

    @Test
    void storedFormIsASha256DigestThatIsNotTheSecret() {
        String secret = Secrets.generate();
        byte[] stored = Secrets.hash(secret);

        assertEquals(32, stored.length, "only the SHA-256 digest is stored");
        assertFalse(java.util.Arrays.equals(secret.getBytes(StandardCharsets.US_ASCII), stored));
    }

    @Test
    void presentedSecretMatchesItsStoredDigest() {
        String secret = Secrets.generate();

        assertTrue(Secrets.matches(secret, Secrets.hash(secret)));
    }

    @Test
    void presentedSecretOfAnotherValueDoesNotMatch() {
        byte[] stored = Secrets.hash(Secrets.generate());

        assertFalse(Secrets.matches(Secrets.generate(), stored),
                "a different secret must not match the stored digest");
    }

    @Test
    void hashIsDeterministicSoLookupsWork() {
        assertArrayEquals(Secrets.hash("same"), Secrets.hash("same"));
    }
}
