package net.onelitefeather.menura.server.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.server.security.AccessFilter;
import net.onelitefeather.menura.server.web.PersonRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order of the access rules: authentication, then the access filter (default deny), then the role check, then the
 * person budget. An anonymous call to a protected route is refused with 401 before any role logic or budget runs, and
 * before the handler runs. Each test builds its own probe state through the injected singleton, cleared before use.
 */
@MicronautTest(rebuildContext = true)
class AccessOrderTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ProtectedProbeController.HandlerCalls calls;

    @BeforeEach
    void noHandlerHasRun() {
        calls.clear();
    }

    @Test
    void anonymousCallerOfARoleProtectedRouteGets401NotA403AndTheHandlerDoesNotRun() {
        HttpClientResponseException refused = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-auth/editor"), String.class));

        assertEquals(401, refused.getStatus().getCode(),
                "without a person the access filter answers first, so the role check never reports 403");
        assertEquals(List.of(), calls.calls(), "a refused request reaches no handler");
    }

    @Test
    void anonymousCallerOfAWriteRouteGets401AndTheHandlerDoesNotRun() {
        HttpClientResponseException refused = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/test-auth/write", ""), String.class));

        assertEquals(401, refused.getStatus().getCode());
        assertEquals(List.of(), calls.calls());
    }

    @Test
    void filtersRunInTheDocumentedOrderAuthenticationAccessRoleBudget() {
        // Only getOrder is read here; the collaborators are not used, so null stands in for them.
        int authentication = new AuthenticationFilter(null, null, Clock.systemUTC()).getOrder();
        int access = new AccessFilter(request -> Optional.empty()).getOrder();
        int role = new RoleCheckFilter().getOrder();
        int budget = new PersonRateLimitFilter(request -> Optional.empty(), null).getOrder();

        assertTrue(authentication < access, "authentication sets the person before the access filter reads it");
        assertTrue(access < role, "the access filter refuses anonymous callers before the role check");
        assertTrue(role < budget, "the person budget is charged only after the role check has passed");
    }
}
