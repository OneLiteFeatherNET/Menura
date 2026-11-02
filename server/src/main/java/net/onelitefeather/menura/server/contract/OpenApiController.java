package net.onelitefeather.menura.server.contract;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves the OpenAPI contract unchanged on the anonymous documentation path. The build copies
 * {@code contracts/openapi/menura.yaml} into the classpath under {@code openapi/}.
 */
@Controller("/openapi")
public class OpenApiController {

    static final String RESOURCE = "/openapi/menura.yaml";

    @Get("/menura.yaml")
    public HttpResponse<byte[]> contract() throws IOException {
        try (InputStream contract = OpenApiController.class.getResourceAsStream(RESOURCE)) {
            if (contract == null) {
                return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return HttpResponse.ok(contract.readAllBytes()).contentType(MediaType.of("application/yaml"));
        }
    }
}
