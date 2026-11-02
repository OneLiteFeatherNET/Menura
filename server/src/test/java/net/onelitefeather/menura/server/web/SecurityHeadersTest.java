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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class SecurityHeadersTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void responsesCarryStrictTransportSecurity() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/test-web/ok").header(HeaderIdentityFilter.PERSON_HEADER, "headers-person"), String.class);

        String hsts = response.header("Strict-Transport-Security");
        assertTrue(hsts != null && hsts.startsWith("max-age="),
                "every response must send Strict-Transport-Security, found: " + hsts);
    }

    @Test
    void responsesSendReferrerPolicyNoReferrer() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/test-web/ok").header(HeaderIdentityFilter.PERSON_HEADER, "headers-person"), String.class);

        assertEquals("no-referrer", response.header("Referrer-Policy"));
    }

    @Test
    void responsesSendNosniff() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/test-web/ok").header(HeaderIdentityFilter.PERSON_HEADER, "headers-person"), String.class);

        assertEquals("nosniff", response.header("X-Content-Type-Options"));
    }

    @Test
    void problemResponsesCarryTheSecurityHeadersToo() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-problem/missing"), String.class));

        assertEquals("nosniff", failure.getResponse().header("X-Content-Type-Options"),
                "the headers are added to every response, not only successful ones");
    }
}
