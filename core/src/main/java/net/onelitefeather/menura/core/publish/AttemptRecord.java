package net.onelitefeather.menura.core.publish;

import java.time.Instant;
import java.util.UUID;

/**
 * One publication attempt of a post. The row is written before the platform is called; the outcome is set afterwards.
 * An attempt without an outcome stopped before the platform answered, and its post is never published again by a retry.
 */
public record AttemptRecord(UUID postId, int attempt, Instant startedAt, Instant finishedAt, String outcome,
                            String reason) {

    public static final String HANDED_OVER = "handed_over";
    public static final String PUBLISHED = "published";
    public static final String RETRY_SCHEDULED = "retry_scheduled";
    public static final String FAILED = "failed";
    public static final String UNKNOWN = "unknown";
}
