package net.onelitefeather.menura.server.health;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
@Property(name = "test.database.reachable", value = "true")
class HealthEndpointTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthAnswersOkWithoutCredentialsWhenDatabaseIsReachable() {
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.GET("/health"), Map.class);

        assertEquals(200, response.code(), "GET /health must answer 200 when the database is reachable");
    }

    @Test
    void healthReportsOnlyStatusAndDatabaseReadiness() {
        Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/health"), Map.class);

        assertEquals(Map.of("status", "UP", "database", "UP"), body,
                "the health body must carry status and database readiness and nothing else");
    }

    @Test
    void healthBodyCarriesNoVersionHostOrConfigurationField() {
        Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/health"), Map.class);

        assertTrue(body.keySet().stream().noneMatch(key -> key.toString().matches("(?i).*(version|host|config).*")),
                "the health body must not expose versions, hosts or configuration, but was " + body);
    }
}
