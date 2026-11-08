package net.onelitefeather.menura.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a JSON body against one component schema of the OpenAPI contract. Every member the schema requires must be
 * present, and a member that is not nullable must never be null. Objects, arrays and one-of alternatives are checked
 * recursively. Types are not compared: the check answers whether a strict client can read the body.
 */
final class ResponseContract {

    private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "menura.yaml");
    private static final String SCHEMA_PREFIX = "#/components/schemas/";

    private final Map<String, Schema> schemas;

    private ResponseContract(Map<String, Schema> schemas) {
        this.schemas = schemas;
    }

    static ResponseContract load() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        OpenAPI api = new OpenAPIParser().readLocation(CONTRACT.toString(), null, options).getOpenAPI();
        return new ResponseContract(api.getComponents().getSchemas());
    }

    /** The violations of the body against the named schema; an empty list when the body conforms. */
    List<String> violations(String schemaName, JsonNode body) {
        Schema<?> schema = schemas.get(schemaName);
        if (schema == null) {
            throw new IllegalArgumentException("the contract declares no schema " + schemaName);
        }
        if (body == null || body.isNull()) {
            return List.of(schemaName + " must not be null");
        }
        return check(schema, body, schemaName);
    }

    private List<String> check(Schema<?> declared, JsonNode value, String path) {
        Schema<?> schema = resolve(declared);
        if (schema.getOneOf() != null) {
            return checkOneOf(schema, value, path);
        }
        List<String> found = new ArrayList<>();
        if (value.isObject() && schema.getProperties() != null) {
            checkObject(schema, value, path, found);
        }
        if (value.isArray() && schema.getItems() != null) {
            for (int i = 0; i < value.size(); i++) {
                found.addAll(check(schema.getItems(), value.get(i), path + "[" + i + "]"));
            }
        }
        return found;
    }

    private void checkObject(Schema<?> schema, JsonNode value, String path, List<String> found) {
        Set<String> required = schema.getRequired() == null ? Set.of() : Set.copyOf(schema.getRequired());
        for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
            String name = property.getKey();
            String at = path + "." + name;
            if (!value.has(name)) {
                if (required.contains(name)) {
                    found.add(at + " is required by the contract but missing");
                }
                continue;
            }
            JsonNode member = value.get(name);
            if (member.isNull()) {
                if (!nullable(property.getValue())) {
                    found.add(at + " is null but the contract does not allow null");
                }
                continue;
            }
            found.addAll(check(property.getValue(), member, at));
        }
    }

    private List<String> checkOneOf(Schema<?> schema, JsonNode value, String path) {
        List<String> firstFailure = null;
        for (Object alternative : schema.getOneOf()) {
            Schema<?> option = resolve((Schema<?>) alternative);
            if (isNullOnly(option)) {
                continue;
            }
            List<String> failure = check(option, value, path);
            if (failure.isEmpty()) {
                return List.of();
            }
            if (firstFailure == null) {
                firstFailure = failure;
            }
        }
        return firstFailure == null ? List.of() : firstFailure;
    }

    private Schema<?> resolve(Schema<?> schema) {
        String ref = schema.get$ref();
        if (ref == null) {
            return schema;
        }
        if (!ref.startsWith(SCHEMA_PREFIX) || !schemas.containsKey(ref.substring(SCHEMA_PREFIX.length()))) {
            throw new IllegalArgumentException("unresolvable schema reference " + ref);
        }
        return schemas.get(ref.substring(SCHEMA_PREFIX.length()));
    }

    private boolean nullable(Schema<?> schema) {
        if (schema.get$ref() != null) {
            return false;
        }
        if (typesOf(schema).contains("null")) {
            return true;
        }
        return schema.getOneOf() != null && schema.getOneOf().stream().anyMatch(alternative -> nullable((Schema<?>) alternative));
    }

    private boolean isNullOnly(Schema<?> schema) {
        return typesOf(schema).equals(Set.of("null"));
    }

    private static Set<String> typesOf(Schema<?> schema) {
        Set<String> types = new HashSet<>();
        if (schema.getTypes() != null) {
            types.addAll(schema.getTypes());
        }
        if (schema.getType() != null) {
            types.add(schema.getType());
        }
        return types;
    }
}
