package net.onelitefeather.menura.core.secret;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretHasherTest {

    private static final String SECRET = "Yw2l8vQ0kE4b1rZxN7sPQ0dHc3tT9uVwXyZaBcDeFgH";

    @Test
    void storedFormIsTheSha256DigestOfTheSecret() throws NoSuchAlgorithmException {
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(SECRET.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(expected, SecretHasher.hash(SECRET),
                "SHA-256 is the stored form: the secret carries 256 bits of entropy, so no slow hash is needed");
    }

    @Test
    void storedFormIsNotTheSecretInClear() {
        byte[] stored = SecretHasher.hash(SECRET);

        assertEquals(32, stored.length, "the stored form is a 32-byte digest");
        assertFalse(Arrays.equals(SECRET.getBytes(StandardCharsets.UTF_8), stored), "the secret must never be stored as is");
    }

    @Test
    void presentedSecretMatchesItsStoredHash() {
        assertTrue(SecretHasher.matches(SECRET, SecretHasher.hash(SECRET)));
    }

    @Test
    void differentSecretDoesNotMatch() {
        String other = SECRET.substring(0, SECRET.length() - 1) + "_";

        assertFalse(SecretHasher.matches(other, SecretHasher.hash(SECRET)));
    }

    @Test
    void truncatedStoredHashDoesNotMatchAndDoesNotFail() {
        byte[] truncated = Arrays.copyOf(SecretHasher.hash(SECRET), 31);

        assertFalse(SecretHasher.matches(SECRET, truncated), "a damaged stored hash must refuse, not throw");
    }
}
