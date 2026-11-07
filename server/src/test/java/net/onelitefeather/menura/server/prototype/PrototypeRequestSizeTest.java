package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The prototype caps the request body at 64 KiB. The cap is set in application-prototype.yml; the server refuses a larger
 * body with 413 before any handler runs.
 */
@MicronautTest(environments = "prototype")
class PrototypeRequestSizeTest {

    private static final int LIMIT = 65_536;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ApplicationContext context;

    @Test
    void theRequestBodyLimitOfThePrototypeIs64KiB() {
        assertEquals(LIMIT, context.getProperty("micronaut.server.max-request-size", Long.class).orElseThrow());
    }

    @Test
    void aBodyLargerThanTheLimitIsRefusedWith413() {
        MutableHttpRequest<String> request = HttpRequest.POST("/api/prototype/series", "x".repeat(LIMIT + 1))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("X-Test-Person", "tester-1")
                .header("X-Test-Roles", "EDITOR");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(request, String.class));

        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, refused.getStatus());
    }
}
