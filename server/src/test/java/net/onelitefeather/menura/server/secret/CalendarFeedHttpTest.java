package net.onelitefeather.menura.server.secret;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class CalendarFeedHttpTest {

    private static final String PERSON = "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f";

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void issuedTokenFetchesTheFeed() {
        String token = issue(PERSON);

        HttpResponse<String> feed = client.toBlocking().exchange(HttpRequest.GET("/calendar/feed/" + token), String.class);

        assertEquals(200, feed.code());
        assertEquals("text/calendar", feed.getContentType().orElseThrow().getName(), "the feed is served as iCalendar");
        assertTrue(feed.body().contains("BEGIN:VCALENDAR"), "the feed body is an iCalendar document");
    }

    @Test
    void issuedTokenIsReturnedWithNoStoreCache() {
        HttpResponse<Map> response = client.toBlocking().exchange(withPerson(HttpRequest.POST("/calendar-feed-token", ""), PERSON), Map.class);

        assertEquals(201, response.code(), "issuing answers 201 Created");
        assertEquals("no-store", response.header("Cache-Control"), "the one-time token must never be cached");
    }

    @Test
    void rotatedTokenIsRefusedAtOnceAndTheNewOneWorks() {
        String previous = issue(PERSON);
        String current = issue(PERSON);

        assertProblem(assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/calendar/feed/" + previous), String.class)), 404);
        assertEquals(200, client.toBlocking().exchange(HttpRequest.GET("/calendar/feed/" + current), String.class).code());
    }

    @Test
    void revokedTokenIsRefusedWith404AsProblemDetails() {
        String token = issue(PERSON);

        HttpResponse<Void> revoked = client.toBlocking().exchange(withPerson(HttpRequest.DELETE("/calendar-feed-token"), PERSON));
        assertEquals(204, revoked.code(), "revoking answers 204 No Content");

        assertProblem(assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/calendar/feed/" + token), String.class)), 404);
    }

    @Test
    void unknownTokenAnswers404WithoutEchoingTheSecretInTheProblem() {
        String forged = UUID.randomUUID() + "." + "Q".repeat(43);

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/calendar/feed/" + forged), String.class));

        assertProblem(failure, 404);
        String body = failure.getResponse().getBody(String.class).orElse("");
        assertFalse(body.contains(forged), "the token must never be echoed in a Problem Details body");
        assertTrue(body.contains("/calendar/feed/{token}"), "the instance names the route, not the token");
    }

    @Test
    void issuingWithoutCredentialsAnswers401AsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/calendar-feed-token", ""), Map.class));

        assertProblem(failure, 401);
    }

    private String issue(String person) {
        HttpResponse<Map> response = client.toBlocking().exchange(withPerson(HttpRequest.POST("/calendar-feed-token", ""), person), Map.class);
        return (String) response.getBody(Map.class).orElseThrow().get("token");
    }

    private static MutableHttpRequest<?> withPerson(MutableHttpRequest<?> request, String person) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person);
    }

    private static void assertProblem(HttpClientResponseException failure, int status) {
        assertEquals(status, failure.getStatus().getCode());
        assertEquals("application/problem+json", failure.getResponse().getContentType().orElseThrow().toString());
    }
}
