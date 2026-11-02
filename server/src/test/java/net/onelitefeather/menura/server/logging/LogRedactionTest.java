package net.onelitefeather.menura.server.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRedactionTest {

    @Test
    void authorizationHeaderValueIsRedacted() {
        String line = LogRedaction.redact("request headers: Authorization: Bearer abc.def.ghi");

        assertFalse(line.contains("abc.def.ghi"), line);
        assertTrue(line.contains("Authorization: [REDACTED]"), line);
    }

    @Test
    void bareBearerTokenIsRedacted() {
        String line = LogRedaction.redact("token was Bearer s3cr3t-value-123");

        assertFalse(line.contains("s3cr3t-value-123"), line);
    }

    @Test
    void sessionCookieValueIsRedactedAndCookieNameKept() {
        String line = LogRedaction.redact("cookie __Host-menura_session=opaque-session-value; other=1");

        assertFalse(line.contains("opaque-session-value"), line);
        assertTrue(line.contains("__Host-menura_session=[REDACTED]"), line);
    }

    @Test
    void setCookieHeaderIsRedacted() {
        String line = LogRedaction.redact("Set-Cookie: __Host-menura_session=opaque; Path=/; HttpOnly");

        assertFalse(line.contains("opaque"), line);
    }

    @Test
    void tokenQueryParameterIsRedactedAndOtherParametersKept() {
        String line = LogRedaction.redact("GET /feeds/calendar?token=feed-secret&lang=de");

        assertFalse(line.contains("feed-secret"), line);
        assertTrue(line.contains("lang=de"), line);
    }

    @Test
    void presignedUrlSignatureIsRedacted() {
        String line = LogRedaction.redact("https://s3.onelitefeather.net/bucket/key?X-Amz-Signature=deadbeef&X-Amz-Expires=300");

        assertFalse(line.contains("deadbeef"), line);
    }

    @Test
    void sceneTokenInQueryIsRedacted() {
        String line = LogRedaction.redact("GET /overlay?scene=scene-secret-token");

        assertFalse(line.contains("scene-secret-token"), line);
    }

    @Test
    void uriIsLoggedWithoutItsQueryValues() {
        assertEquals("/feeds/calendar?[REDACTED]", LogRedaction.redactUri("/feeds/calendar?token=feed-secret"));
        assertEquals("/health", LogRedaction.redactUri("/health"));
    }

    @Test
    void textWithoutSecretsIsUnchanged() {
        String line = "Unexpected error while handling /library/recordings";

        assertEquals(line, LogRedaction.redact(line));
    }
}
