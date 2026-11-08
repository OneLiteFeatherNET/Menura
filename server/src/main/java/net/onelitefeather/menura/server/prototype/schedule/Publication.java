package net.onelitefeather.menura.server.prototype.schedule;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

/**
 * One clip on one channel at one time. {@code assetId} is null for a published post of the v1 history, which has no
 * clip in the pool. {@code mode} is {@link #NATIVE} when the platform schedules itself, {@link #HUB} otherwise.
 * {@code snapshots} is always empty here: the metrics come from the analytics endpoints.
 */
@Serdeable
public record Publication(String id, String assetId, String channelId, String platform, String seriesId,
                          Instant publishAt, String mode, Status status, Metadata metadata, List<Step> steps,
                          String postId, List<Snapshot> snapshots) {

    public static final String NATIVE = "native";
    public static final String HUB = "hub";

    public Publication {
        steps = List.copyOf(steps);
        snapshots = List.copyOf(snapshots);
    }

    public Publication withPublishAt(Instant value) {
        return new Publication(id, assetId, channelId, platform, seriesId, value, mode, status, metadata, steps,
                postId, snapshots);
    }

    public Publication withMetadata(Metadata value) {
        return new Publication(id, assetId, channelId, platform, seriesId, publishAt, mode, status, value, steps,
                postId, snapshots);
    }

    public enum Status { planned, uploading, scheduled, published, failed }

    /** The metadata as it goes to the platform. Tags are hashtags; the playlist is null when none is set. */
    @Serdeable
    public record Metadata(String title, String description, List<String> tags, String playlist) {

        public Metadata {
            tags = List.copyOf(tags);
        }
    }

    @Serdeable
    public record Snapshot(String offset, Instant at, long views, Long engagedViews, long likes) {
    }
}
