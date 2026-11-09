package net.onelitefeather.menura.server.attribution;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The proxy's credential and budget: 401 without a valid credential, 403 for a signed-in person, 429 with Retry-After over
 * the budget. The clock is fixed, so the budget never refills during a test.
 */
class JoinProxyGuardTest {

    /** A fake credential; its digest is the one the test configuration holds. */
    private static final String CREDENTIAL = "test-join-proxy-credential";
    private static final String DIGEST = "e5e7cd8bacd0b66b4564fcf31f0f87a1ed1e29f2223e216d51e54d8bff4dff05";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void aRequestWithoutCredentialAnswers401() {
        JoinProxyGuard guard = new JoinProxyGuard(DIGEST, CLOCK);

        MutableHttpResponse<?> refusal = guard.refusalFor(HttpRequest.POST("/join-events", "{}"));

        assertNotNull(refusal, "a request without a credential is refused");
        assertEquals(HttpStatus.UNAUTHORIZED, refusal.getStatus(), "without a credential the answer is 401");
    }

    @Test
    void aWrongCredentialAnswers401AndDoesNotEchoIt() {
        JoinProxyGuard guard = new JoinProxyGuard(DIGEST, CLOCK);
        HttpRequest<?> request = HttpRequest.POST("/join-events", "{}").header("Authorization", "Bearer guessed-value");

        MutableHttpResponse<?> refusal = guard.refusalFor(request);

        assertEquals(HttpStatus.UNAUTHORIZED, refusal.getStatus(), "a wrong credential is 401");
        assertFalse(String.valueOf(refusal.getBody().orElse(null)).contains("guessed-value"),
                "the presented value is not echoed");
    }

    @Test
    void aSignedInPersonWithoutTheProxyCredentialAnswers403() {
        JoinProxyGuard guard = new JoinProxyGuard(DIGEST, CLOCK);
        HttpRequest<?> request = HttpRequest.POST("/join-events", "{}");
        request.setAttribute(CurrentPerson.ATTRIBUTE,
                new CurrentPerson(UUID.randomUUID(), "subject", Set.of(Role.ADMIN), Channel.WEB));

        MutableHttpResponse<?> refusal = guard.refusalFor(request);

        assertEquals(HttpStatus.FORBIDDEN, refusal.getStatus(), "a person may not send join events");
    }

    @Test
    void theProxyCredentialPassesAndSpendsOneRequestOfItsBudget() {
        JoinProxyGuard guard = new JoinProxyGuard(DIGEST, CLOCK);
        HttpRequest<?> request = HttpRequest.POST("/join-events", "{}").header("Authorization", "Bearer " + CREDENTIAL);

        assertNull(guard.refusalFor(request), "the proxy's credential within its budget is admitted");
    }

    @Test
    void noCredentialIsAdmittedWhileTheProxyIsNotConfigured() {
        JoinProxyGuard guard = new JoinProxyGuard("", CLOCK);
        HttpRequest<?> request = HttpRequest.POST("/join-events", "{}").header("Authorization", "Bearer " + CREDENTIAL);

        assertEquals(HttpStatus.UNAUTHORIZED, guard.refusalFor(request).getStatus(),
                "an unconfigured proxy keeps every join call closed");
    }

    @Test
    void aCallOverTheBudgetAnswers429WithRetryAfter() {
        JoinProxyGuard guard = new JoinProxyGuard(DIGEST, CLOCK);
        HttpRequest<?> request = HttpRequest.POST("/join-events", "{}").header("Authorization", "Bearer " + CREDENTIAL);
        for (int i = 0; i < JoinProxyGuard.REQUESTS_PER_MINUTE; i++) {
            assertNull(guard.refusalFor(request), "the first " + JoinProxyGuard.REQUESTS_PER_MINUTE + " calls are admitted");
        }

        MutableHttpResponse<?> refusal = guard.refusalFor(request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, refusal.getStatus(), "the call over the budget is 429");
        assertTrue(refusal.getHeaders().contains("Retry-After"), "the refusal carries Retry-After");
    }

    @Test
    void aDigestThatIsNotSixtyFourHexDigitsStopsTheStart() {
        assertThrows(IllegalStateException.class, () -> JoinProxyGuard.digestOf("not-a-digest"),
                "a malformed digest is a configuration error");
    }
}
