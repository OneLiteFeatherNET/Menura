package net.onelitefeather.menura.server.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieTest {

    @Test
    void sessionCookieHasTheHostPrefixAndAllRequiredAttributes() {
        String header = SessionCookie.set("opaque-value", Duration.ofHours(8));

        assertTrue(header.startsWith("__Host-menura-session=opaque-value;"), header);
        assertTrue(header.contains("Path=/"), "a __Host- cookie must have Path=/");
        assertTrue(header.contains("Secure"), header);
        assertTrue(header.contains("HttpOnly"), header);
        assertTrue(header.contains("SameSite=Strict"), header);
        assertTrue(header.contains("Max-Age=28800"), header);
    }

    @Test
    void sessionCookieIsHostOnly() {
        String header = SessionCookie.set("opaque-value", Duration.ofHours(8));

        assertFalse(header.contains("Domain="), "a __Host- cookie must not name a Domain, so it stays host-only");
    }

    @Test
    void expiredCookieClearsTheSameNameWithTheSameAttributes() {
        String header = SessionCookie.expire();

        assertTrue(header.startsWith("__Host-menura-session=;"), header);
        assertTrue(header.contains("Max-Age=0"), header);
        assertTrue(header.contains("Path=/"), header);
        assertTrue(header.contains("Secure"), header);
    }

    @Test
    void valueWithSeparatorsOrWhitespaceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SessionCookie.set("a;b", Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> SessionCookie.set("a b", Duration.ofHours(1)));
    }

    @Test
    void nonPositiveLifetimeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SessionCookie.set("value", Duration.ZERO));
    }

    @Test
    void cookieNameIsTheSessionName() {
        assertEquals("__Host-menura-session", SessionCookie.NAME);
    }
}
