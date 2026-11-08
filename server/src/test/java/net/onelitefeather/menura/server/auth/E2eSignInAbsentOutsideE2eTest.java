package net.onelitefeather.menura.server.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** In environment prod the test-only sign-in does not exist, so its path answers 404 and issues no cookie. */
@MicronautTest(environments = "prod", rebuildContext = true)
class E2eSignInAbsentOutsideE2eTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testOnlySignInAnswers404InProdAndIssuesNoCookie() {
        var refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/auth/e2e/sign-in?role=admin"), String.class));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals(null, refused.getResponse().getHeaders().get("Set-Cookie"), "no session cookie is issued");
    }
}
