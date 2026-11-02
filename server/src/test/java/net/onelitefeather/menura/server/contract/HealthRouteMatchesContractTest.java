package net.onelitefeather.menura.server.contract;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(rebuildContext = true)
class HealthRouteMatchesContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "menura.yaml");

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthRouteAnswersTheStatusCodesDeclaredInTheContract() {
        Set<String> declared = contract().getPaths().get("/health").getGet().getResponses().keySet();

        assertTrue(declared.contains("200"), "the contract must declare 200 for GET /health");
        assertEquals(200, client.toBlocking().exchange(HttpRequest.GET("/health"), Map.class).code(),
                "the code must answer the declared 200 when the database is reachable");
    }

    @Test
    void healthBodyFieldsMatchTheContractSchema() {
        Schema<?> schema = contract().getComponents().getSchemas().get("HealthStatus");
        assertNotNull(schema, "the contract must declare the HealthStatus schema");

        Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/health"), Map.class);

        assertEquals(schema.getProperties().keySet(), body.keySet(),
                "the health body must carry exactly the properties the contract declares");
    }

    private static OpenAPI contract() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        return new OpenAPIParser().readLocation(CONTRACT.toString(), null, options).getOpenAPI();
    }
}
