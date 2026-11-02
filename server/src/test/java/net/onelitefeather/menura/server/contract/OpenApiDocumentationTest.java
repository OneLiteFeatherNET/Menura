package net.onelitefeather.menura.server.contract;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(rebuildContext = true)
class OpenApiDocumentationTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "menura.yaml");

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void contractIsServedUnchangedOnTheAnonymousDocumentationPath() throws IOException {
        HttpResponse<byte[]> response = client.toBlocking().exchange(
                HttpRequest.GET("/openapi/menura.yaml"), byte[].class);

        assertEquals(200, response.code(), "the documentation path must answer 200 without credentials");
        assertArrayEquals(Files.readAllBytes(CONTRACT), response.body(),
                "the served contract must be byte-for-byte the file in contracts/openapi");
    }

    @Test
    void contractIsServedAsYaml() {
        HttpResponse<byte[]> response = client.toBlocking().exchange(
                HttpRequest.GET("/openapi/menura.yaml"), byte[].class);

        assertEquals("application/yaml", response.getContentType().orElseThrow().toString(),
                "the contract must be served with the YAML media type");
    }
}
