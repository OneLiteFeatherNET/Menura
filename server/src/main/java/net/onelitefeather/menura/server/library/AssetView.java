package net.onelitefeather.menura.server.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A verified asset as listed: its metadata only, never a link. Nullable members are always sent, as null. */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetView(UUID id, String kind, String format, Integer durationSeconds, int width, int height,
                        long sizeBytes, String checksum, UUID uploaderId, String streamTitle, LocalDate streamDate,
                        Instant createdAt) {
}
