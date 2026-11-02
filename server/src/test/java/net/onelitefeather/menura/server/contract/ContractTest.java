package net.onelitefeather.menura.server.contract;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lints the OpenAPI contract, the source of truth for every endpoint, and checks the shared components. */
class ContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "menura.yaml");

    private static OpenAPI contract() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(CONTRACT.toString(), null, options);
        assertTrue(result.getMessages().isEmpty(), "the contract must lint without messages, but got " + result.getMessages());
        assertNotNull(result.getOpenAPI(), "the contract must parse as OpenAPI 3.1");
        return result.getOpenAPI();
    }

    @Test
    void contractLintsWithoutMessages() {
        contract();
    }

    @Test
    void contractDeclaresTheHealthOperation() {
        OpenAPI api = contract();

        assertNotNull(api.getPaths().get("/health"), "the contract must declare GET /health");
        assertNotNull(api.getPaths().get("/health").getGet(), "GET /health must be an operation");
    }

    @Test
    void contractDeclaresTheSharedComponents() {
        OpenAPI api = contract();

        assertNotNull(api.getComponents().getSchemas().get("ProblemDetails"), "Problem Details schema is required");
        assertNotNull(api.getComponents().getSchemas().get("CursorPage"), "the cursor paging envelope is required");
        assertNotNull(api.getComponents().getSchemas().get("Identifier"), "the identifier schema is required");
        assertNotNull(api.getComponents().getResponses().get("Problem"), "a shared problem response is required");
    }
}
