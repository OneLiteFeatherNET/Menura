package net.onelitefeather.menura.server.health;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/** Anonymous liveness and readiness probe. Serves the same route for both, as the contract describes. */
@Controller
public class HealthController {

    private final DatabaseProbe databaseProbe;

    public HealthController(DatabaseProbe databaseProbe) {
        this.databaseProbe = databaseProbe;
    }

    @Get("/health")
    public HttpResponse<HealthStatus> health() {
        boolean reachable = databaseProbe.isReachable();
        HealthStatus body = HealthStatus.of(reachable);
        return reachable
                ? HttpResponse.ok(body)
                : HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
