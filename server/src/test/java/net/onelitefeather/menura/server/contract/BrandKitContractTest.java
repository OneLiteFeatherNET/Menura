package net.onelitefeather.menura.server.contract;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The brand kit endpoints are declared in the contract, every one of them with the session cookie, and their schemas exist. */
class BrandKitContractTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "menura.yaml");

    private static OpenAPI contract() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        return new OpenAPIParser().readLocation(CONTRACT.toString(), null, options).getOpenAPI();
    }

    @Test
    void everyBrandKitRouteIsDeclaredWithTheSessionCookie() {
        OpenAPI api = contract();

        assertSecured(api, "/brand-kit", PathItem::getGet, "current kit");
        assertSecured(api, "/brand-kit/versions", PathItem::getPost, "publish a version");
        assertSecured(api, "/brand-kit/versions/{version}", PathItem::getGet, "read a version");
        assertSecured(api, "/brand-kit/render-bundle", PathItem::getGet, "render bundle");
        assertSecured(api, "/brand-kit/css", PathItem::getGet, "css export");
        assertSecured(api, "/brand-kit/templates/{format}", PathItem::getGet, "template by format");
        assertSecured(api, "/brand-kit/texts/{language}", PathItem::getGet, "text by language");
        assertSecured(api, "/brand-kit/pins/{consumer}", PathItem::getGet, "read a pin");
        assertSecured(api, "/brand-kit/pins/{consumer}", PathItem::getPut, "set a pin");
        assertSecured(api, "/brand-kit/pins/{consumer}", PathItem::getDelete, "remove a pin");
    }

    @Test
    void theBrandKitSchemasAreDeclared() {
        OpenAPI api = contract();

        for (String schema : List.of("BrandKit", "BrandKitVersion", "ColourToken", "TonalRamp", "TextRole", "TypographyRole",
                "Mark", "LanguageText", "Template", "Slot", "Timing", "Parameter", "KitPin", "KitPinRequest", "ConsumerName")) {
            assertNotNull(api.getComponents().getSchemas().get(schema), schema + " must be declared in the contract");
        }
    }

    @Test
    void thePublishOperationDocumentsTheRefusalAsUnprocessable() {
        Operation publish = operation(contract(), "/brand-kit/versions", PathItem::getPost);

        assertNotNull(publish.getResponses().get("422"), "a refused publication is documented with 422");
        assertTrue(publish.getDescription().contains("stores nothing"), "the refusal stores nothing, as the contract says");
        assertEquals("publishBrandKitVersion", publish.getOperationId());
    }

    private static void assertSecured(OpenAPI api, String path, Function<PathItem, Operation> method, String what) {
        Operation op = operation(api, path, method);
        assertEquals(List.of("sessionCookie"), op.getSecurity().stream().flatMap(r -> r.keySet().stream()).toList(),
                what + " must apply the session cookie");
    }

    private static Operation operation(OpenAPI api, String path, Function<PathItem, Operation> method) {
        PathItem item = api.getPaths().get(path);
        assertNotNull(item, path + " must be declared");
        Operation op = method.apply(item);
        assertNotNull(op, path + " must declare the operation");
        assertNotNull(op.getSecurity(), path + " must state its security explicitly");
        return op;
    }
}
