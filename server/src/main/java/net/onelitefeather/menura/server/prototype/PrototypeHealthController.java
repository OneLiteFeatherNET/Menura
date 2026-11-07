package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.server.health.HealthController;

/**
 * Readiness of the prototype: there is no database to probe, so the answer names the in-memory store. It replaces the
 * production health route only in the prototype environment. The route stays anonymous (see AccessFilter).
 */
@Controller("/health")
@Requires(env = "prototype")
@Replaces(HealthController.class)
public class PrototypeHealthController {

    @Get
    PrototypeHealth health() {
        return new PrototypeHealth("UP", "IN_MEMORY");
    }

    @Serdeable
    public record PrototypeHealth(String status, String database) {
    }
}
