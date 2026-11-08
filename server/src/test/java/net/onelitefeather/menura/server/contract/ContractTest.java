package net.onelitefeather.menura.server.contract;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void sessionCookieSchemeIsDeclaredOnceWithTheHostPrefixName() {
        OpenAPI api = contract();

        SecurityScheme scheme = api.getComponents().getSecuritySchemes().get("sessionCookie");
        assertNotNull(scheme, "the sessionCookie security scheme is required");
        assertEquals(SecurityScheme.Type.APIKEY, scheme.getType());
        assertEquals(SecurityScheme.In.COOKIE, scheme.getIn());
        assertEquals("__Host-menura-session", scheme.getName(), "the cookie carries the __Host- prefix");
    }

    @Test
    void protectedOperationsApplyTheSessionCookie() {
        OpenAPI api = contract();

        assertEquals(List.of("sessionCookie"), securityOf(api, "/auth/session", PathItem::getGet),
                "the session of the web app is the cookie");
        assertEquals(List.of("sessionCookie"), securityOf(api, "/calendar-feed-token", PathItem::getPost),
                "issuing a calendar feed token needs a session");
        assertEquals(List.of("sessionCookie"), securityOf(api, "/calendar-feed-token", PathItem::getDelete),
                "revoking a calendar feed token needs a session");
    }

    @Test
    void anonymousOperationsDeclareNoSecurity() {
        OpenAPI api = contract();

        assertEquals(List.of(), securityOf(api, "/health", PathItem::getGet));
        assertEquals(List.of(), securityOf(api, "/auth/login", PathItem::getGet));
        assertEquals(List.of(), securityOf(api, "/auth/callback", PathItem::getGet));
        assertEquals(List.of(), securityOf(api, "/auth/logout", PathItem::getPost));
        assertEquals(List.of(), securityOf(api, "/calendar/feed/{token}", PathItem::getGet),
                "the feed authenticates with its own token in the path");
    }

    @Test
    void channelOperationsNeedASessionAndTheCallbackToo() {
        OpenAPI api = contract();

        assertEquals(List.of("sessionCookie"), securityOf(api, "/channels", PathItem::getGet));
        assertEquals(List.of("sessionCookie"), securityOf(api, "/channels/connections", PathItem::getPost),
                "starting a connection needs a session");
        assertEquals(List.of("sessionCookie"), securityOf(api, "/channels/{id}", PathItem::getDelete),
                "disconnecting needs a session");
        assertEquals(List.of("sessionCookie"), securityOf(api, "/channel-connections/callback", PathItem::getGet),
                "the callback is tied to the person who started the consent");
        assertEquals(List.of("sessionCookie"), securityOf(api, "/platforms", PathItem::getGet));
    }

    @Test
    void channelRepresentationDeclaresNoTokenProperty() {
        OpenAPI api = contract();

        for (String schema : List.of("Channel", "AuditView", "QuotaView", "Platform", "Authorization")) {
            io.swagger.v3.oas.models.media.Schema<?> declared = api.getComponents().getSchemas().get(schema);
            assertNotNull(declared, schema + " must be declared");
            assertNotNull(declared.getProperties(), schema + " must list its properties");
            for (Object property : declared.getProperties().keySet()) {
                assertFalse(property.toString().toLowerCase(java.util.Locale.ROOT).contains("token"),
                        schema + " must not declare a token property, but declares " + property);
            }
        }
    }

    /** The names of the security requirements of one operation; empty when it declares none. */
    private static List<String> securityOf(OpenAPI api, String path, Function<PathItem, Operation> operation) {
        Operation op = operation.apply(api.getPaths().get(path));
        assertNotNull(op, path + " must declare the operation");
        assertNotNull(op.getSecurity(), path + " must state its security explicitly");
        return op.getSecurity().stream().flatMap(requirement -> requirement.keySet().stream()).toList();
    }
}
