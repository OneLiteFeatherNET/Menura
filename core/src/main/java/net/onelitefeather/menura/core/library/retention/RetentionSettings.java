package net.onelitefeather.menura.core.library.retention;

import java.time.Duration;
import java.util.Map;

/**
 * The retention policy of raw recordings, read from the environment of the jobs image. The policy is disabled by
 * default; enabling it is a deliberate decision per deployment (MENURA_RETENTION_ENABLED=true).
 *
 * <ul>
 *   <li>{@code MENURA_RETENTION_ENABLED}: true to move raw media; default false.</li>
 *   <li>{@code MENURA_RETENTION_RAW_MAX_AGE}: an ISO-8601 duration; raw media created before now minus this is moved
 *       to cold storage. Default P30D.</li>
 *   <li>{@code MENURA_RETENTION_BATCH_SIZE}: items per run, 1 to 1000. Default 100.</li>
 * </ul>
 */
public record RetentionSettings(boolean enabled, Duration rawMaxAge, int batchSize) {

    public static final int MAX_BATCH = 1000;

    public RetentionSettings {
        if (rawMaxAge == null || rawMaxAge.isNegative() || rawMaxAge.isZero()) {
            throw new IllegalArgumentException("MENURA_RETENTION_RAW_MAX_AGE must be a positive duration");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH) {
            throw new IllegalArgumentException("MENURA_RETENTION_BATCH_SIZE must be between 1 and " + MAX_BATCH);
        }
    }

    public static RetentionSettings from(Map<String, String> environment) {
        boolean enabled = Boolean.parseBoolean(environment.getOrDefault("MENURA_RETENTION_ENABLED", "false").trim());
        Duration maxAge = Duration.parse(environment.getOrDefault("MENURA_RETENTION_RAW_MAX_AGE", "P30D").trim());
        int batch = Integer.parseInt(environment.getOrDefault("MENURA_RETENTION_BATCH_SIZE", "100").trim());
        return new RetentionSettings(enabled, maxAge, batch);
    }
}
