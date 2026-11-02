package net.onelitefeather.menura.server.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactingConverterTest {

    @Test
    void logOutputPassedThroughTheConverterHasNoSecret() {
        RedactingConverter converter = new RedactingConverter();

        String output = converter.transform(null, "call with Authorization: Bearer leaked-token and token=leaked-query");

        assertFalse(output.contains("leaked-token"), output);
        assertFalse(output.contains("leaked-query"), output);
        assertTrue(output.contains("[REDACTED]"), output);
    }
}
