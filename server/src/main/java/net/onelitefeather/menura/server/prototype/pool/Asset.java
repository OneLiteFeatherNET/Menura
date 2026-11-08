package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

/**
 * A recording or a clip of the pool. {@code sourceAssetId} points at the raw recording a clip was cut from (null for
 * recordings). The enum constants are the wire values: they serialise in lower case as in the prototype contract.
 */
@Serdeable
public record Asset(String id, Kind kind, String title, String ownerId, List<String> people, List<String> tags,
                    long durationSeconds, Instant createdAt, String sourceAssetId, String seriesId, Stage stage,
                    Transcript transcript, long sizeBytes) {

    public Asset {
        people = List.copyOf(people);
        tags = List.copyOf(tags);
    }

    public enum Kind { raw, clip }

    public enum Stage { raw, cut, rendered }

    public enum Transcript { none, pending, ready }
}
