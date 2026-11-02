package net.onelitefeather.menura.server.web;

import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Budget of two requests per hour for each person. The window is an hour so no refill happens during the test,
 * and the person is taken from the test header rather than a sign-in, so the test needs no sleeps and no clock.
 */
@MicronautTest(rebuildContext = true)
@Property(name = "menura.ratelimit.person.requests", value = "2")
@Property(name = "menura.ratelimit.person.window-seconds", value = "3600")
class PersonRateLimitFilterTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void requestBeyondThePersonBudgetAnswers429WithRetryAfter() {
        exchangeAs("person-limit-1");
        exchangeAs("person-limit-1");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> exchangeAs("person-limit-1"));

        assertEquals(429, refused.getStatus().getCode());
        String retryAfter = refused.getResponse().header("Retry-After");
        assertTrue(retryAfter != null && Long.parseLong(retryAfter) >= 1 && Long.parseLong(retryAfter) <= 3600,
                "Retry-After must be a positive number of seconds, found: " + retryAfter);
    }

    @Test
    void refusedRequestAnswersWithProblemDetails() {
        exchangeAs("person-limit-2");
        exchangeAs("person-limit-2");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> exchangeAs("person-limit-2"));

        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
        Map<?, ?> body = refused.getResponse().getBody(Map.class).orElseThrow();
        assertEquals(429, body.get("status"));
        assertEquals("/test-web/ok", body.get("instance"));
    }

    @Test
    void anotherPersonIsNotLimitedByTheFirstPersonsBudget() {
        exchangeAs("person-limit-3");
        exchangeAs("person-limit-3");

        HttpResponse<String> response = exchangeAs("person-other-3");

        assertEquals(200, response.code(), "one person's budget must not be shared with another person");
    }

    @Test
    void anonymousRequestIsRefusedBeforeAnyPersonBudgetIsCharged() {
        for (int i = 0; i < 5; i++) {
            HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/test-web/ok"), String.class));
            assertEquals(401, refused.getStatus().getCode(),
                    "an anonymous request on a protected route is refused by the access filter, before the budget");
        }

        assertEquals(200, exchangeAs("person-after-anonymous").code(),
                "anonymous refusals leave every person's budget untouched");
        assertEquals(200, exchangeAs("person-after-anonymous").code());
    }

    private HttpResponse<String> exchangeAs(String person) {
        return client.toBlocking().exchange(HttpRequest.GET("/test-web/ok").header("X-Test-Person", person),
                String.class);
    }
}
