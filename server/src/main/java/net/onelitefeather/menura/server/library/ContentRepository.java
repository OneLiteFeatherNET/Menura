package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.library.ClipRange;
import net.onelitefeather.menura.core.library.ClipStatus;
import net.onelitefeather.menura.core.library.Stage;
import net.onelitefeather.menura.core.library.TranscriptSegment;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores the content library: clips, tags, transcripts and their segments, timelines, lineage edges and the registered
 * finished assets. Every call runs on its transaction's connection. Raw items are the verified raw-material rows of
 * the asset table; this repository reads them and never changes their upload.
 */
public interface ContentRepository {

    /** A raw item: a verified raw-material asset of the asset table, listed in the pool. */
    record RawItem(UUID id, UUID uploaderId, String title, LocalDate streamDate, int durationSeconds, Instant createdAt,
                   String storageKey, String mediaState) {
    }

    /** A clip: a time range of a raw item. The raw item's identifier and the range are fixed once the clip exists. */
    record ClipRecord(UUID id, UUID rawItemId, UUID uploaderId, String title, ClipRange range, ClipStatus status,
                      Stage stage, Instant createdAt, Instant updatedAt) {
    }

    /**
     * A row of the pool: a raw item, a clip, or a registered finished asset (listed as a clip at stage rendered).
     * {@code used} is true when a timeline refers to a clip; it is always false for other rows.
     */
    record PoolRow(UUID id, String kind, Stage stage, ClipStatus status, UUID uploaderId, String title, UUID rawItemId,
                   UUID assetId, Integer startSeconds, Integer endSeconds, boolean used, Instant createdAt) {
    }

    /** A transcript segment as the search returns it, with the raw item it belongs to. */
    record SearchHit(UUID rawItemId, String title, LocalDate streamDate, UUID uploaderId, TranscriptSegment segment) {
    }

    record TimelineRecord(UUID id, UUID uploaderId, String title, String documentJson, Instant createdAt) {
    }

    record RegisteredAsset(UUID assetId, UUID uploaderId, String title, UUID timelineId, Instant registeredAt) {
    }

    /** One lineage walk from a raw item: a finished asset, the timeline it was made from and one clip of that timeline. */
    record LineageRow(UUID rawItemId, UUID assetId, String assetTitle, UUID timelineId, UUID clipId) {
    }

    /** A channel a finished asset was published on: its identifier and display name. */
    record ChannelRef(UUID id, String name) {
    }

    /** The tags of an item: free tags and the people (person tags) who appear in it. */
    record ItemTags(Set<String> freeTags, Set<UUID> personTags) {
    }

    Optional<RawItem> findRawItem(Connection connection, UUID id) throws SQLException;

    Optional<ClipRecord> findClip(Connection connection, UUID id) throws SQLException;

    void insertClip(Connection connection, ClipRecord clip) throws SQLException;

    /** Writes the mutable fields of a clip: title, status, stage and the update time. */
    void updateClip(Connection connection, ClipRecord clip) throws SQLException;

    void deleteClip(Connection connection, UUID id) throws SQLException;

    /** Sets the media state of a raw item: available, cold or removed. */
    void setRawMediaState(Connection connection, UUID rawItem, String state) throws SQLException;

    /** The given persons that exist; a person tag may only name one of them. */
    Set<UUID> existingPersons(Connection connection, Collection<UUID> persons) throws SQLException;

    /** Tags of each of the given items; an item without tags has an empty entry. */
    Map<UUID, ItemTags> tagsOf(Connection connection, Collection<UUID> items) throws SQLException;

    /** Replaces both tag sets of an item. */
    void replaceTags(Connection connection, UUID item, ItemTags tags) throws SQLException;

    /**
     * The channels each of the given items was published on. An item is published on a channel when a finished asset
     * made from it (a timeline that uses the item or one of its clips) was published from a Menura post on that channel.
     * An item that was never published has no entry.
     */
    Map<UUID, List<ChannelRef>> publishedOn(Connection connection, Collection<UUID> items) throws SQLException;

    /**
     * One page of the pool, newest first, after the keyset position (both null for the first page). The filter is
     * applied in the same statement; the caller has checked who may read the pool.
     */
    List<PoolRow> pool(Connection connection, PoolFilter filter, Instant afterCreatedAt, UUID afterId, int limit)
            throws SQLException;

    /** Whether a raw item already has a transcript. */
    boolean hasTranscript(Connection connection, UUID rawItem) throws SQLException;

    /**
     * Replaces all transcript segments of a raw item and records the transcript. Returns true when a transcript was
     * already there and has been replaced.
     */
    boolean replaceTranscript(Connection connection, UUID rawItem, String format, UUID uploaderId, Instant uploadedAt,
                              List<TranscriptSegment> segments) throws SQLException;

    /** Segments whose text contains every word of the query, earliest first within the newest recordings. */
    List<SearchHit> search(Connection connection, String query, PoolFilter filter, int limit) throws SQLException;

    void insertTimeline(Connection connection, TimelineRecord timeline) throws SQLException;

    Optional<TimelineRecord> findTimeline(Connection connection, UUID id) throws SQLException;

    /** Adds a lineage edge; an existing identical edge is kept once. */
    void insertEdge(Connection connection, String childKind, UUID childId, String parentKind, UUID parentId)
            throws SQLException;

    /** Whether a timeline or a finished asset refers to the clip, so the clip has been used. */
    boolean isClipUsed(Connection connection, UUID clipId) throws SQLException;

    /** The clips a timeline uses, in no particular order. */
    Set<UUID> clipsOf(Connection connection, UUID timelineId) throws SQLException;

    /** Every finished asset made from clips of the raw item, with its timeline and that clip. */
    List<LineageRow> lineageOf(Connection connection, UUID rawItem) throws SQLException;

    Optional<RegisteredAsset> findRegistration(Connection connection, UUID assetId) throws SQLException;

    void insertRegistration(Connection connection, RegisteredAsset registration) throws SQLException;

    /** The filters of the pool and of search; a null member is not applied. */
    record PoolFilter(UUID uploaderId, UUID personId, String tag, Stage stage, String usage, LocalDate from,
                      LocalDate to) {

        public static final PoolFilter NONE = new PoolFilter(null, null, null, null, null, null, null);
    }
}
