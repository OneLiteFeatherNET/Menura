package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.library.ClipStatus;
import net.onelitefeather.menura.core.library.Stage;
import net.onelitefeather.menura.core.library.TranscriptSegment;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * In-memory content library with the same filter rules as the SQL of {@link JdbcContentRepository}: the pool and
 * search apply the filters, the keyset order is created_at desc, id desc, and a search matches every word of the query.
 * Each test builds its own instance.
 */
public final class InMemoryContentRepository implements ContentRepository {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private final Map<UUID, RawItem> rawItems = new LinkedHashMap<>();
    private final Map<UUID, ClipRecord> clips = new LinkedHashMap<>();
    private final Map<UUID, ItemTags> tags = new HashMap<>();
    private final Map<UUID, RegisteredAsset> registrations = new LinkedHashMap<>();
    private final Map<UUID, TimelineRecord> timelines = new LinkedHashMap<>();
    private final Map<UUID, List<IndexedSegment>> segments = new HashMap<>();
    private final Map<UUID, String> transcriptFormats = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private long nextSegment;

    private record IndexedSegment(long order, TranscriptSegment segment) {
    }

    private record Edge(String childKind, UUID childId, String parentKind, UUID parentId) {
    }

    public void addRawItem(RawItem raw) {
        rawItems.put(raw.id(), raw);
    }

    /** The format of the transcript last stored for a raw item, or null when it has none. */
    public String transcriptFormat(UUID rawItem) {
        return transcriptFormats.get(rawItem);
    }

    /** The number of stored segments of a raw item. */
    public int segmentCount(UUID rawItem) {
        return segments.getOrDefault(rawItem, List.of()).size();
    }

    public List<ClipRecord> clips() {
        return List.copyOf(clips.values());
    }

    @Override
    public Optional<RawItem> findRawItem(Connection connection, UUID id) {
        return Optional.ofNullable(rawItems.get(id));
    }

    @Override
    public Optional<ClipRecord> findClip(Connection connection, UUID id) {
        return Optional.ofNullable(clips.get(id));
    }

    @Override
    public void insertClip(Connection connection, ClipRecord clip) {
        clips.put(clip.id(), clip);
    }

    @Override
    public void updateClip(Connection connection, ClipRecord clip) {
        clips.put(clip.id(), clip);
    }

    @Override
    public void deleteClip(Connection connection, UUID id) {
        clips.remove(id);
        tags.remove(id);
    }

    @Override
    public Map<UUID, ItemTags> tagsOf(Connection connection, Collection<UUID> items) {
        Map<UUID, ItemTags> found = new HashMap<>();
        for (UUID item : items) {
            found.put(item, tags.getOrDefault(item, new ItemTags(Set.of(), Set.of())));
        }
        return found;
    }

    @Override
    public void replaceTags(Connection connection, UUID item, ItemTags replacement) {
        tags.put(item, new ItemTags(Set.copyOf(replacement.freeTags()), Set.copyOf(replacement.personTags())));
    }

    @Override
    public List<PoolRow> pool(Connection connection, PoolFilter filter, Instant afterCreatedAt, UUID afterId, int limit) {
        List<PoolRow> rows = new ArrayList<>();
        for (RawItem raw : rawItems.values()) {
            rows.add(new PoolRow(raw.id(), "raw", Stage.RAW, ClipStatus.ACTIVE, raw.uploaderId(), raw.title(), null, null,
                    null, null, isRawUsedNow(raw.id()), raw.createdAt()));
        }
        for (ClipRecord clip : clips.values()) {
            rows.add(new PoolRow(clip.id(), "clip", clip.stage(), clip.status(), clip.uploaderId(), clip.title(),
                    clip.rawItemId(), null, clip.range().startSeconds(), clip.range().endSeconds(), isClipUsedNow(clip.id()),
                    clip.createdAt()));
        }
        for (RegisteredAsset registration : registrations.values()) {
            rows.add(new PoolRow(registration.assetId(), "clip", Stage.RENDERED, ClipStatus.ACTIVE,
                    registration.uploaderId(), registration.title(), null, registration.assetId(), null, null, false,
                    registration.registeredAt()));
        }
        return rows.stream()
                .filter(row -> matchesRow(row, filter))
                .filter(row -> afterCreatedAt == null || row.createdAt().isBefore(afterCreatedAt)
                        || (row.createdAt().equals(afterCreatedAt) && row.id().compareTo(afterId) < 0))
                .sorted(Comparator.comparing(PoolRow::createdAt).thenComparing(PoolRow::id).reversed())
                .limit(limit)
                .toList();
    }

    private boolean matchesRow(PoolRow row, PoolFilter filter) {
        if (filter.uploaderId() != null && !filter.uploaderId().equals(row.uploaderId())) {
            return false;
        }
        if (filter.personId() != null && !personTagsOf(row.id()).contains(filter.personId())) {
            return false;
        }
        if (filter.tag() != null && !freeTagsOf(row.id()).contains(filter.tag())) {
            return false;
        }
        if (filter.stage() != null && filter.stage() != row.stage()) {
            return false;
        }
        if (filter.usage() != null && row.used() != "used".equals(filter.usage())) {
            return false;
        }
        LocalDate created = LocalDate.ofInstant(row.createdAt(), ZoneOffset.UTC);
        return (filter.from() == null || !created.isBefore(filter.from()))
                && (filter.to() == null || !created.isAfter(filter.to()));
    }

    @Override
    public boolean hasTranscript(Connection connection, UUID rawItem) {
        return transcriptFormats.containsKey(rawItem);
    }

    @Override
    public boolean replaceTranscript(Connection connection, UUID rawItem, String format, UUID uploaderId, Instant uploadedAt,
                                     List<TranscriptSegment> replacement) {
        boolean replaced = transcriptFormats.containsKey(rawItem);
        transcriptFormats.put(rawItem, format);
        List<IndexedSegment> stored = new ArrayList<>();
        for (TranscriptSegment segment : replacement) {
            stored.add(new IndexedSegment(nextSegment++, segment));
        }
        segments.put(rawItem, stored);
        return replaced;
    }

    @Override
    public List<SearchHit> search(Connection connection, String query, PoolFilter filter, int limit) {
        Set<String> wanted = words(query);
        List<SearchHit> hits = new ArrayList<>();
        for (Map.Entry<UUID, List<IndexedSegment>> entry : segments.entrySet()) {
            RawItem raw = rawItems.get(entry.getKey());
            if (raw == null || !matchesRaw(raw, filter)) {
                continue;
            }
            for (IndexedSegment indexed : entry.getValue()) {
                if (words(indexed.segment().text()).containsAll(wanted)) {
                    hits.add(new SearchHit(raw.id(), raw.title(), raw.streamDate(), raw.uploaderId(), indexed.segment()));
                }
            }
        }
        hits.sort(Comparator.comparing((SearchHit hit) -> hit.streamDate(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingLong(hit -> hit.segment().startMillis()));
        return hits.subList(0, Math.min(limit, hits.size()));
    }

    private boolean matchesRaw(RawItem raw, PoolFilter filter) {
        if (filter.uploaderId() != null && !filter.uploaderId().equals(raw.uploaderId())) {
            return false;
        }
        if (filter.personId() != null && !personTagsOf(raw.id()).contains(filter.personId())) {
            return false;
        }
        if (filter.tag() != null && !freeTagsOf(raw.id()).contains(filter.tag())) {
            return false;
        }
        if (filter.stage() != null && filter.stage() != Stage.RAW && clips.values().stream()
                .noneMatch(clip -> clip.rawItemId().equals(raw.id()) && clip.stage() == filter.stage())) {
            return false;
        }
        if (filter.usage() != null && clips.values().stream().filter(clip -> clip.rawItemId().equals(raw.id()))
                .anyMatch(clip -> isClipUsedNow(clip.id())) != "used".equals(filter.usage())) {
            return false;
        }
        return (filter.from() == null || raw.streamDate() == null || !raw.streamDate().isBefore(filter.from()))
                && (filter.to() == null || raw.streamDate() == null || !raw.streamDate().isAfter(filter.to()));
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    @Override
    public void insertTimeline(Connection connection, TimelineRecord timeline) {
        timelines.put(timeline.id(), timeline);
    }

    @Override
    public Optional<TimelineRecord> findTimeline(Connection connection, UUID id) {
        return Optional.ofNullable(timelines.get(id));
    }

    @Override
    public void insertEdge(Connection connection, String childKind, UUID childId, String parentKind, UUID parentId) {
        Edge edge = new Edge(childKind, childId, parentKind, parentId);
        if (!edges.contains(edge)) {
            edges.add(edge);
        }
    }

    @Override
    public boolean isClipUsed(Connection connection, UUID clipId) {
        return isClipUsedNow(clipId);
    }

    /** A raw item is used when one of its clips is in a timeline, as the pool and search SQL decide. */
    private boolean isRawUsedNow(UUID rawItem) {
        return clips.values().stream().filter(clip -> clip.rawItemId().equals(rawItem))
                .anyMatch(clip -> isClipUsedNow(clip.id()));
    }

    private boolean isClipUsedNow(UUID clipId) {
        return edges.stream().anyMatch(edge -> edge.parentKind().equals("clip") && edge.parentId().equals(clipId));
    }

    @Override
    public Set<UUID> clipsOf(Connection connection, UUID timelineId) {
        Set<UUID> found = new HashSet<>();
        for (Edge edge : edges) {
            if (edge.childKind().equals("timeline") && edge.childId().equals(timelineId)) {
                found.add(edge.parentId());
            }
        }
        return found;
    }

    @Override
    public List<LineageRow> lineageOf(Connection connection, UUID rawItem) {
        List<LineageRow> rows = new ArrayList<>();
        for (RegisteredAsset registration : registrations.values()) {
            for (UUID clip : clipsOf(connection, registration.timelineId())) {
                ClipRecord record = clips.get(clip);
                if (record != null && record.rawItemId().equals(rawItem)) {
                    rows.add(new LineageRow(rawItem, registration.assetId(), registration.title(),
                            registration.timelineId(), clip));
                }
            }
        }
        return rows;
    }

    @Override
    public Optional<RegisteredAsset> findRegistration(Connection connection, UUID assetId) {
        return Optional.ofNullable(registrations.get(assetId));
    }

    @Override
    public void insertRegistration(Connection connection, RegisteredAsset registration) {
        registrations.put(registration.assetId(), registration);
    }

    private Set<String> freeTagsOf(UUID item) {
        return tags.getOrDefault(item, new ItemTags(Set.of(), Set.of())).freeTags();
    }

    private Set<UUID> personTagsOf(UUID item) {
        return tags.getOrDefault(item, new ItemTags(Set.of(), Set.of())).personTags();
    }
}
