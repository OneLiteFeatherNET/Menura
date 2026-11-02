package net.onelitefeather.menura.server.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The anonymous allow-list and the Problem Details answers of the access rules. */
@MicronautTest(rebuildContext = true)
@Property(name = "test.database.reachable", value = "true")
class AccessHttpTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthIsAnswerableWithoutCredentials() {
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.GET("/health"), Map.class);

        assertEquals(200, response.code(), "health is on the anonymous allow-list");
    }

    @Test
    void openApiDocumentIsAnswerableWithoutCredentials() {
        HttpResponse<byte[]> response = client.toBlocking().exchange(HttpRequest.GET("/openapi/menura.yaml"), byte[].class);

        assertEquals(200, response.code(), "the OpenAPI document is on the anonymous allow-list");
    }

    @Test
    void protectedRouteWithoutCredentialsAnswers401AsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/test-audit/write", ""), Map.class));

        assertProblem(failure, 401, "/test-audit/write");
        assertTrue(failure.getResponse().getHeaders().contains("WWW-Authenticate"),
                "a 401 must tell the client how to authenticate (RFC 9110)");
    }

    @Test
    void unknownRouteWithoutCredentialsAnswers401AndRevealsNothingAboutRoutes() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/does-not-exist"), Map.class));

        assertProblem(failure, 401, "/does-not-exist");
    }

    @Test
    void unknownRouteWithCredentialsAnswers404AsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(withPerson(HttpRequest.GET("/does-not-exist")), Map.class));

        assertProblem(failure, 404, "/does-not-exist");
    }

    @Test
    void forbiddenAnswers403AsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(withPerson(HttpRequest.GET("/test-security/forbidden")), Map.class));

        assertProblem(failure, 403, "/test-security/forbidden");
    }

    @Test
    void rateLimitedAnswers429AsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(withPerson(HttpRequest.GET("/test-security/rate-limited")), Map.class));

        assertProblem(failure, 429, "/test-security/rate-limited");
    }

    @Test
    void problemBodyNeverRepeatsTheRequestQueryOrSecrets() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-security/forbidden?token=" + UUID.randomUUID()), Map.class));

        assertFalse(failure.getResponse().getBody(String.class).orElse("").contains("token="),
                "the instance is the path only; the query string is not echoed");
    }

    private static MutableHttpRequest<?> withPerson(MutableHttpRequest<?> request) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, UUID.randomUUID().toString());
    }

    private static void assertProblem(HttpClientResponseException failure, int status, String instance) {
        assertEquals(status, failure.getStatus().getCode());
        assertEquals("application/problem+json", failure.getResponse().getContentType().orElseThrow().toString(),
                "every refusal must be sent as application/problem+json");
        Map<?, ?> body = failure.getResponse().getBody(Map.class).orElseThrow();
        assertEquals(status, body.get("status"));
        assertEquals(instance, body.get("instance"));
        assertTrue(body.containsKey("title") && body.containsKey("detail") && body.containsKey("type"),
                "the Problem Details body must carry type, title and detail");
    }
}
