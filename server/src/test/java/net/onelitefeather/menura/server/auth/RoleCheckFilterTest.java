package net.onelitefeather.menura.server.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
class RoleCheckFilterTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ProtectedProbeController.HandlerCalls handlerCalls;

    @BeforeEach
    void noHandlerHasRunYet() {
        handlerCalls.clear();
    }

    @Test
    void viewerWriteIsRefusedWith403AndTheHandlerDoesNotRun() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/test-auth/write", "")
                        .header(TestPersonFilter.ROLES_HEADER, "VIEWER"), String.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus(), "a viewer's write must be refused with 403");
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
        assertTrue(handlerCalls.calls().isEmpty(), "the handler must not run for a refused request");
    }

    @Test
    void editorWriteRunsTheHandler() {
        var response = client.toBlocking().exchange(HttpRequest.POST("/test-auth/write", "")
                .header(TestPersonFilter.ROLES_HEADER, "EDITOR"), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(List.of("write"), handlerCalls.calls(), "an allowed request must reach its handler once");
    }

    @Test
    void endpointListingSeveralRolesAcceptsAnyOfThem() {
        var response = client.toBlocking().exchange(HttpRequest.POST("/test-auth/write", "")
                .header(TestPersonFilter.ROLES_HEADER, "ADMIN"), String.class);

        assertEquals(HttpStatus.OK, response.getStatus(), "an admin passes an endpoint that lists admin");
    }

    @Test
    void requestWithoutAPersonIsRefusedWith401BeforeTheHandler() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-auth/editor"), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus(), "an anonymous caller must get 401");
        assertEquals(List.of(), handlerCalls.calls(), "the handler must not run without a person");
    }

    @Test
    void signedInPersonWithoutTheRoleIsRefusedForAnEndpointThatNamesTheRole() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/test-auth/editor")
                        .header(TestPersonFilter.ROLES_HEADER, ""), Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
        assertEquals(List.of(), handlerCalls.calls());
    }

    @Test
    void endpointWithoutRolesAcceptsAnySignedInPerson() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/test-auth/signed-in")
                .header(TestPersonFilter.ROLES_HEADER, ""), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(List.of("signed-in"), handlerCalls.calls());
    }
}
