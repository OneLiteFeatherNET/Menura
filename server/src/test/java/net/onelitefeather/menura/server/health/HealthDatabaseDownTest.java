package net.onelitefeather.menura.server.health;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(rebuildContext = true)
@Property(name = "test.database.reachable", value = "false")
class HealthDatabaseDownTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthAnswers503WhenDatabaseIsUnreachable() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/health"), Map.class));

        assertEquals(503, failure.getStatus().getCode(), "readiness must be down when the database is unreachable");
    }

    @Test
    void healthBodyReportsDatabaseDown() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/health"), Map.class));

        Map<?, ?> body = failure.getResponse().getBody(Map.class).orElseThrow();
        assertEquals(Map.of("status", "DOWN", "database", "DOWN"), body,
                "the body must report the database as DOWN");
    }
}
