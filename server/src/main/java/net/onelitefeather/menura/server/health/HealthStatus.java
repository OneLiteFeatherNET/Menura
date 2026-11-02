package net.onelitefeather.menura.server.health;

import io.micronaut.serde.annotation.Serdeable;

/** Body of GET /health: the overall status and the database readiness, nothing else. */
@Serdeable
public record HealthStatus(String status, String database) {

    public static HealthStatus of(boolean databaseReachable) {
        String state = databaseReachable ? "UP" : "DOWN";
        return new HealthStatus(state, state);
    }
}
