package net.onelitefeather.menura.server.web;

import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Asserts the session cookie as it leaves the fake session endpoint on api-menura's wire format. */
@MicronautTest(rebuildContext = true)
class SessionCookieHttpTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void sessionEndpointSetsExactlyOneSessionCookieWithTheHostPrefix() {
        List<String> cookies = sessionCookies();

        assertEquals(1, cookies.size(), "the session endpoint must set exactly one cookie: " + cookies);
        assertTrue(cookies.get(0).startsWith("__Host-menura-session="), cookies.get(0));
    }

    @Test
    void sessionCookieIsSecureHttpOnlyAndSameSiteStrict() {
        String cookie = sessionCookies().get(0);

        assertTrue(cookie.contains("; Secure"), cookie);
        assertTrue(cookie.contains("; HttpOnly"), cookie);
        assertTrue(cookie.contains("; SameSite=Strict"), cookie);
    }

    @Test
    void sessionCookieIsHostOnlyOnThePathRoot() {
        String cookie = sessionCookies().get(0);

        assertTrue(cookie.contains("; Path=/"), cookie);
        assertFalse(cookie.contains("Domain="), "a __Host- cookie must not carry a Domain attribute: " + cookie);
    }

    @Test
    void sessionCookieCarriesNoTokenMaterial() {
        String cookie = sessionCookies().get(0);

        assertTrue(cookie.startsWith("__Host-menura-session=opaque-session-value;"),
                "the cookie value is an opaque session id, not a token: " + cookie);
    }

    private List<String> sessionCookies() {
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.POST("/test-web/session", "").header(HeaderIdentityFilter.PERSON_HEADER, "cookie-person"), String.class);
        return response.getHeaders().getAll("Set-Cookie");
    }
}
