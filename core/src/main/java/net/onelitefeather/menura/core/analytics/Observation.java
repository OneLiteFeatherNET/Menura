package net.onelitefeather.menura.core.analytics;

import java.time.Instant;
import java.util.UUID;

/**
 * One value of one metric of one external post, with the dimensions the aggregates group by. The value is null when a
 * snapshot holds no value because the platform did not provide the metric; aggregates skip such values.
 *
 * @param seriesId the series of the post as collected, or null when it belongs to none
 */
public record Observation(UUID postId, UUID channelId, String platform, String format, UUID seriesId,
                          Instant publishedAt, String metric, Long value) {
}
