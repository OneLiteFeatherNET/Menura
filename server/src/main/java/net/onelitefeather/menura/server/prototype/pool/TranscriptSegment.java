package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.serde.annotation.Serdeable;

/** One timed line of a ready transcript. {@code personId} is null when no person is named for the line. */
@Serdeable
public record TranscriptSegment(double startSeconds, double endSeconds, String personId, String text) {
}
