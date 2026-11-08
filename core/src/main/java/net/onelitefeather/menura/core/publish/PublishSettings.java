package net.onelitefeather.menura.core.publish;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

/**
 * The publication and calendar settings, read from the environment with local-dev defaults (principle 8).
 *
 * <ul>
 *   <li>{@code MENURA_PUBLISH_MAX_ATTEMPTS}: attempts per post, 1 to 10, default 3.</li>
 *   <li>{@code MENURA_PUBLISH_RETRY_DELAY}: the first retry delay as an ISO-8601 duration, default PT5M; it doubles.</li>
 *   <li>{@code MENURA_PUBLISH_HANDOVER_LEAD}: how long before its time a natively scheduled post is handed to the
 *       platform, default PT24H.</li>
 *   <li>{@code MENURA_PUBLISH_BATCH_SIZE}: posts one job run claims, 1 to 100, default 10.</li>
 *   <li>{@code MENURA_PUBLISH_LEASE}: how long a claim lasts before another run may take the post, default PT10M.</li>
 *   <li>{@code MENURA_CALENDAR_TIME_ZONE}: the time zone every calendar time is shown in, default Europe/Berlin.</li>
 * </ul>
 */
public record PublishSettings(RetryPolicy retry, Duration handoverLead, int batchSize, Duration lease, ZoneId zone) {

    public static final String MAX_ATTEMPTS = "MENURA_PUBLISH_MAX_ATTEMPTS";
    public static final String RETRY_DELAY = "MENURA_PUBLISH_RETRY_DELAY";
    public static final String HANDOVER_LEAD = "MENURA_PUBLISH_HANDOVER_LEAD";
    public static final String BATCH_SIZE = "MENURA_PUBLISH_BATCH_SIZE";
    public static final String LEASE = "MENURA_PUBLISH_LEASE";
    public static final String TIME_ZONE = "MENURA_CALENDAR_TIME_ZONE";

    public PublishSettings {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("the batch size must be between 1 and 100");
        }
        if (handoverLead.isNegative()) {
            throw new IllegalArgumentException("the handover lead must not be negative");
        }
        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("the lease must be positive");
        }
    }

    public static PublishSettings from(Map<String, String> environment) {
        return new PublishSettings(
                new RetryPolicy(integer(environment, MAX_ATTEMPTS, 3), duration(environment, RETRY_DELAY, "PT5M")),
                duration(environment, HANDOVER_LEAD, "PT24H"),
                integer(environment, BATCH_SIZE, 10),
                duration(environment, LEASE, "PT10M"),
                ZoneId.of(value(environment, TIME_ZONE, "Europe/Berlin")));
    }

    private static String value(Map<String, String> environment, String name, String fallback) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int integer(Map<String, String> environment, String name, int fallback) {
        return Integer.parseInt(value(environment, name, Integer.toString(fallback)));
    }

    private static Duration duration(Map<String, String> environment, String name, String fallback) {
        return Duration.parse(value(environment, name, fallback));
    }
}
