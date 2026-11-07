package net.onelitefeather.menura.server.prototype;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A body that is not valid JSON is refused with 400, and the answer does not repeat the submitted text. The token is
 * an unquoted word, which is what the JSON parser quotes back in its message if the error is echoed.
 */
@MicronautTest(environments = "prototype")
class PrototypeMalformedJsonTest {

    private static final String SUBMITTED_TOKEN = "Echo7731Payload";

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void aMalformedSeriesBodyIsRefusedWith400() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(editor(malformed()), String.class));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
    }

    @Test
    void theAnswerToAMalformedSeriesBodyDoesNotEchoTheSubmittedText() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(editor(malformed()), String.class));

        String answer = refused.getResponse().getBody(String.class).orElse("");
        assertFalse(answer.contains(SUBMITTED_TOKEN), "the body must not echo the input: " + answer);
        assertFalse(String.valueOf(refused.getMessage()).contains(SUBMITTED_TOKEN),
                "the exception message must not echo the input");
    }

    private static MutableHttpRequest<String> malformed() {
        return HttpRequest.POST("/api/prototype/series", "{\"name\": " + SUBMITTED_TOKEN + "}");
    }

    private static MutableHttpRequest<String> editor(MutableHttpRequest<String> request) {
        return request.contentType(MediaType.APPLICATION_JSON_TYPE)
                .header("X-Test-Person", "tester-1")
                .header("X-Test-Roles", "EDITOR");
    }
}
