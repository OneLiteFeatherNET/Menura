package net.onelitefeather.menura.server.library;

import io.micronaut.serde.annotation.Serdeable;

/**
 * What an editor declares to start an upload. The numbers are boxed so that a missing field is reported by name.
 */
@Serdeable
public record AssetUploadRequest(String kind, String format, Integer durationSeconds, Integer width, Integer height,
                                 Long sizeBytes, String checksum, String streamTitle, String streamDate) {
}
