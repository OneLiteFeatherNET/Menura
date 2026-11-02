package net.onelitefeather.menura.server.web;

import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
class CorsTest {

    private static final String WEB_APP = "https://menura.onelitefeather.net";

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void credentialedRequestFromTheWebAppHostIsAllowedForThatOriginOnly() {
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.GET("/test-web/ok").header("Origin", WEB_APP).header(HeaderIdentityFilter.PERSON_HEADER, "cors-person"), String.class);

        assertEquals(WEB_APP, response.header("Access-Control-Allow-Origin"),
                "the API must echo only the web app origin, never a wildcard");
        assertEquals("true", response.header("Access-Control-Allow-Credentials"),
                "the web app sends its session cookie, so credentials must be allowed for that origin");
    }

    @Test
    void foreignOriginIsRefusedWithNoCrossOriginPermission() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/test-web/ok").header("Origin", "https://evil.example"), String.class));

        assertEquals(403, refused.getStatus().getCode(), "the CORS filter refuses a foreign origin");
        assertNull(refused.getResponse().header("Access-Control-Allow-Origin"),
                "a foreign origin must receive no Access-Control-Allow-Origin header");
        assertNull(refused.getResponse().header("Access-Control-Allow-Credentials"));
    }

    @Test
    void lookalikeHostIsNotGranted() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/test-web/ok")
                        .header("Origin", "https://menura.onelitefeather.net.evil.example"), String.class));

        assertNull(refused.getResponse().header("Access-Control-Allow-Origin"),
                "a host that merely starts with the web app host must not be allowed");
    }
}
