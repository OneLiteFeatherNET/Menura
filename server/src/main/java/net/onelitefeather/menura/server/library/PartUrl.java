package net.onelitefeather.menura.server.library;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

/** A presigned URL for one part of an upload. The browser sends the part's bytes to it with a PUT. */
@Serdeable
public record PartUrl(int partNumber, long sizeBytes, String url, Instant expiresAt) {
}
