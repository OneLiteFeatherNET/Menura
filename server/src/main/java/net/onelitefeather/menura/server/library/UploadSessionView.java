package net.onelitefeather.menura.server.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An upload session as the editor sees it: its state, the confirmed parts and the URLs of the parts still to send.
 * Nullable members are always sent, as null, so every client sees the same shape.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UploadSessionView(UUID id, String state, String kind, String format, Integer durationSeconds,
                                int width, int height, long sizeBytes, String checksum, long partSizeBytes,
                                int partCount, String streamTitle, LocalDate streamDate, UUID duplicateOf,
                                UUID assetId, String failureReason, List<Integer> confirmedParts,
                                Integer nextPart, List<PartUrl> parts, Instant createdAt, Instant updatedAt) {
}
