package net.onelitefeather.menura.server.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The responses of the content library. Lists are always sent, even when empty. */
public final class ContentViews {

    private ContentViews() {
    }

    /** A clip. Its stage is {@code cut} until a finished asset made from it is registered, then {@code rendered}. */
    @Serdeable
    public record ClipView(UUID id, UUID rawItemId, UUID uploaderId, String title, int startSeconds, int endSeconds,
                           String status, String stage, List<String> freeTags, Instant createdAt) {
    }

    @Serdeable
    public record ItemTagsView(UUID itemId, List<String> freeTags, List<UUID> personTags) {
    }

    /**
     * One item of the pool. {@code kind} is {@code raw} for a stream recording and {@code clip} otherwise; a registered
     * finished asset is a clip at stage rendered with its {@code assetId} set. {@code usage} is set for clips only.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PoolItemView(UUID id, String kind, String stage, String status, UUID uploaderId, String title,
                               UUID rawItemId, UUID assetId, Integer startSeconds, Integer endSeconds, String usage,
                               List<UUID> personTags, List<String> freeTags, Instant createdAt) {
    }

    /** One cursor page of the pool; {@code nextCursor} is null on the last page. */
    @Serdeable
    public record PoolPageView(List<PoolItemView> items, String nextCursor) {
    }

    /** A search hit: the segment, its raw item and the second the speech starts at. */
    @Serdeable
    public record SearchHitView(UUID rawItemId, String title, LocalDate streamDate, UUID uploaderId, long startMillis,
                                long endMillis, String speaker, String text) {
    }

    @Serdeable
    public record TranscriptView(UUID rawItemId, String format, int segmentCount, boolean replaced, Instant uploadedAt) {
    }

    @Serdeable
    public record TimelineView(UUID id, String title, UUID uploaderId, TimelineDocument document, Instant createdAt) {
    }

    @Serdeable
    public record RegisteredAssetView(UUID assetId, String title, UUID uploaderId, UUID timelineId, Instant registeredAt) {
    }

    /** A finished asset made from a raw item, with its timeline and the clips of that raw item it uses. */
    @Serdeable
    public record LineageAssetView(UUID assetId, String title, UUID timelineId, List<UUID> clipIds) {
    }

    @Serdeable
    public record RawLineageView(UUID rawItemId, List<LineageAssetView> assets) {
    }
}
