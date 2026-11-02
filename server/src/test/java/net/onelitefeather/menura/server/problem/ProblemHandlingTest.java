package net.onelitefeather.menura.server.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
class ProblemHandlingTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void problemExceptionIsSentAsProblemDetailsWithAllFiveMembers() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-problem/missing"), Map.class));

        assertEquals(404, failure.getStatus().getCode());
        assertEquals("application/problem+json", failure.getResponse().getContentType().orElseThrow().toString(),
                "problem bodies must be sent as application/problem+json");
        Map<?, ?> body = failure.getResponse().getBody(Map.class).orElseThrow();
        assertEquals("about:blank", body.get("type"));
        assertEquals("Not Found", body.get("title"));
        assertEquals(404, body.get("status"));
        assertEquals("No test item has this id.", body.get("detail"));
        assertEquals("/test-problem/missing", body.get("instance"));
    }

    @Test
    void unexpectedErrorDoesNotLeakItsMessageToTheClient() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-problem/boom"), Map.class));

        assertEquals(500, failure.getStatus().getCode());
        Map<?, ?> body = failure.getResponse().getBody(Map.class).orElseThrow();
        assertEquals(500, body.get("status"));
        assertFalse(body.get("detail").toString().contains("secret internal detail"),
                "internal error messages must never reach the client");
    }
}
