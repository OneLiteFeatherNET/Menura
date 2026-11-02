package net.onelitefeather.menura.core.id;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentifierGeneratorTest {

    @Test
    void identifierIsVersion4AndRfc4122VariantFromTheInjectedRandomSource() {
        IdentifierGenerator generator = new IdentifierGenerator(fixedBytes((byte) 0xFF));

        UUID id = generator.next();

        assertEquals(4, id.version(), "identifiers must be RFC 9562 version 4 UUIDs");
        assertEquals(2, id.variant(), "identifiers must use the RFC 4122 variant");
        assertEquals("ffffffff-ffff-4fff-bfff-ffffffffffff", id.toString());
    }

    @Test
    void identifierIsDerivedOnlyFromTheRandomSourceAndHasNoTimeComponent() {
        IdentifierGenerator generator = new IdentifierGenerator(fixedBytes((byte) 0x00));

        UUID first = generator.next();
        UUID second = generator.next();

        assertEquals("00000000-0000-4000-8000-000000000000", first.toString(),
                "with a fixed random source the identifier must be exactly the random bits with version and variant set");
        assertEquals(first, second, "the same random bytes must yield the same identifier at any time of day");
    }

    private static SecureRandom fixedBytes(byte value) {
        return new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, value);
            }
        };
    }
}
