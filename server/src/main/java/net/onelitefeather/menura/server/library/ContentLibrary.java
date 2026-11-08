package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.library.ClipRange;
import net.onelitefeather.menura.core.library.ClipStatus;
import net.onelitefeather.menura.core.library.FreeTag;
import net.onelitefeather.menura.core.library.Stage;
import net.onelitefeather.menura.core.paging.Cursor;
import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.library.ContentRepository.ClipRecord;
import net.onelitefeather.menura.server.library.ContentRepository.ItemTags;
import net.onelitefeather.menura.server.library.ContentRepository.LineageRow;
import net.onelitefeather.menura.server.library.ContentRepository.PoolFilter;
import net.onelitefeather.menura.server.library.ContentRepository.PoolRow;
import net.onelitefeather.menura.server.library.ContentRepository.RawItem;
import net.onelitefeather.menura.server.library.ContentRepository.SearchHit;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The shared pool: the listing with its filters, free and person tags, clips (create, retitle, suggest, delete),
 * transcript search and the lineage of a raw item. Reading needs any Menura role; every change needs the editor role
 * and runs with its audit entry in one transaction. Editing follows the role, never the uploader.
 */
@Singleton
public class ContentLibrary {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_SEARCH_LIMIT = 100;
    private static final String CURSOR_SEPARATOR = "|";

    /** The query of the pool and of search. {@code mine} selects the caller's own items; {@code stage} and {@code usage} are names. */
    public record PoolQuery(boolean mine, UUID uploaderId, UUID personId, String tag, String stage, String usage,
                            LocalDate from, LocalDate to) {

        public static final PoolQuery NONE = new PoolQuery(false, null, null, null, null, null, null, null);
    }

    /** A clip to create: its raw item, range in seconds, title, free tags and optional status (active by default). */
    public record ClipRequest(UUID rawItemId, Integer startSeconds, Integer endSeconds, String title, Set<String> freeTags,
                              String status) {
    }

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final ContentRepository content;
    private final AuditLog auditLog;

    public ContentLibrary(Transactions transactions, Clock clock, IdentifierGenerator identifiers, ContentRepository content,
                          AuditLog auditLog) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.content = content;
        this.auditLog = auditLog;
    }

    /** One page of the pool the viewer may read, newest first, with the filters applied together. */
    public ContentViews.PoolPageView pool(CurrentPerson viewer, PoolQuery query, String cursor, int limit) throws SQLException {
        PageRequest page = PageRequest.of(limit, cursor);
        if (!AssetAccess.mayRead(viewer)) {
            return new ContentViews.PoolPageView(List.of(), null);
        }
        PoolFilter filter = filter(viewer, query);
        Instant after = null;
        UUID afterId = null;
        if (page.afterKey() != null) {
            String[] position = page.afterKey().split("\\" + CURSOR_SEPARATOR, -1);
            if (position.length != 2) {
                throw invalidCursor();
            }
            after = parseInstant(position[0]);
            afterId = parseUuid(position[1]);
        }
        Instant seek = after;
        UUID seekId = afterId;
        int fetch = page.limit() + 1;
        List<ContentViews.PoolItemView> items = transactions.inTransaction(connection -> {
            List<PoolRow> rows = content.pool(connection, filter, seek, seekId, fetch);
            Map<UUID, ItemTags> tags = content.tagsOf(connection, rows.stream().map(PoolRow::id).toList());
            return rows.stream().map(row -> poolItem(row, tags.get(row.id()))).toList();
        });
        boolean more = items.size() > page.limit();
        List<ContentViews.PoolItemView> shown = more ? items.subList(0, page.limit()) : items;
        String next = null;
        if (more) {
            ContentViews.PoolItemView last = shown.get(shown.size() - 1);
            next = Cursor.encode(last.createdAt() + CURSOR_SEPARATOR + last.id());
        }
        return new ContentViews.PoolPageView(shown, next);
    }

    /** Replaces the free and person tags of a raw item or clip. Viewers are refused. */
    public ContentViews.ItemTagsView setTags(CurrentPerson viewer, UUID item, Set<String> freeTags, Set<UUID> personTags,
                                             String requestId) throws SQLException {
        requireEditor(viewer);
        Set<String> free = FreeTag.parseAll(freeTags == null ? Set.of() : freeTags);
        Set<UUID> persons = personTags == null ? Set.of() : Set.copyOf(personTags);
        return auditLog.write(AssetUploads.caller(viewer), "item.tags", "item:" + item, requestId, connection -> {
            requireItem(connection, item);
            content.replaceTags(connection, item, new ItemTags(free, persons));
            return tagsView(item, new ItemTags(free, persons));
        });
    }

    /** Creates a clip inside its raw item, at stage cut. Its status is active, or suggested when asked for. */
    public ContentViews.ClipView createClip(CurrentPerson viewer, ClipRequest request, String requestId) throws SQLException {
        requireEditor(viewer);
        if (request.rawItemId() == null) {
            throw new ProblemException(400, "Invalid clip", "The field 'rawItemId' is required.");
        }
        String title = requireTitle(request.title());
        ClipStatus status = ClipStatus.parse(request.status());
        Set<String> freeTags = FreeTag.parseAll(request.freeTags() == null ? Set.of() : request.freeTags());
        UUID id = identifiers.next();
        return auditLog.write(AssetUploads.caller(viewer), "clip.create", "clip:" + id, requestId, connection -> {
            RawItem raw = content.findRawItem(connection, request.rawItemId()).orElseThrow(() -> rawNotFound());
            ClipRange range = ClipRange.of(requireSeconds("startSeconds", request.startSeconds()),
                    requireSeconds("endSeconds", request.endSeconds())).within(raw.durationSeconds());
            Instant now = clock.instant();
            ClipRecord clip = new ClipRecord(id, raw.id(), viewer.personId(), title, range, status, Stage.CUT, now, now);
            content.insertClip(connection, clip);
            content.replaceTags(connection, id, new ItemTags(freeTags, Set.of()));
            return clipView(clip, freeTags);
        });
    }

    /** Changes the title of a clip; the uploader stays the same. */
    public ContentViews.ClipView renameClip(CurrentPerson viewer, UUID id, String title, String requestId) throws SQLException {
        requireEditor(viewer);
        String checked = requireTitle(title);
        return auditLog.write(AssetUploads.caller(viewer), "clip.rename", "clip:" + id, requestId, connection -> {
            ClipRecord clip = content.findClip(connection, id).orElseThrow(() -> clipNotFound());
            ClipRecord renamed = new ClipRecord(clip.id(), clip.rawItemId(), clip.uploaderId(), checked, clip.range(),
                    clip.status(), clip.stage(), clip.createdAt(), clock.instant());
            content.updateClip(connection, renamed);
            return clipView(renamed, content.tagsOf(connection, List.of(id)).get(id).freeTags());
        });
    }

    /**
     * Deletes a clip on an explicit request, with one audit entry. A clip a timeline uses is kept: its lineage must stay
     * readable, so the deletion is refused with 409.
     */
    public void deleteClip(CurrentPerson viewer, UUID id, String requestId) throws SQLException {
        requireEditor(viewer);
        auditLog.write(AssetUploads.caller(viewer), "clip.delete", "clip:" + id, requestId, connection -> {
            content.findClip(connection, id).orElseThrow(() -> clipNotFound());
            if (content.isClipUsed(connection, id)) {
                throw new ProblemException(409, "Clip In Use", "A timeline uses this clip, so it cannot be deleted.");
            }
            content.deleteClip(connection, id);
            return null;
        });
    }

    /** The clip with the given id, for the viewer who may read the pool. */
    public ContentViews.ClipView clip(CurrentPerson viewer, UUID id) throws SQLException {
        if (!AssetAccess.mayRead(viewer)) {
            throw clipNotFound();
        }
        return transactions.inTransaction(connection -> {
            ClipRecord clip = content.findClip(connection, id).orElseThrow(() -> clipNotFound());
            return clipView(clip, content.tagsOf(connection, List.of(id)).get(id).freeTags());
        });
    }

    /**
     * Suggests highlight candidates: full-text hits of the words of the query, each with the start of its speech.
     * Filters apply as in the pool. A blank query is refused.
     */
    public List<ContentViews.SearchHitView> search(CurrentPerson viewer, String query, PoolQuery filter, int limit)
            throws SQLException {
        if (query == null || query.isBlank()) {
            throw new ProblemException(400, "Invalid search", "The query must not be empty.");
        }
        if (limit < 1 || limit > MAX_SEARCH_LIMIT) {
            throw new ProblemException(400, "Invalid search", "The limit must be between 1 and " + MAX_SEARCH_LIMIT + ".");
        }
        if (!AssetAccess.mayRead(viewer)) {
            return List.of();
        }
        PoolFilter converted = filter(viewer, filter);
        String wanted = query.trim();
        List<SearchHit> hits = transactions.inTransaction(connection -> content.search(connection, wanted, converted, limit));
        return hits.stream().map(hit -> new ContentViews.SearchHitView(hit.rawItemId(), hit.title(), hit.streamDate(),
                hit.uploaderId(), hit.segment().startMillis(), hit.segment().endMillis(), hit.segment().speaker(),
                hit.segment().text())).toList();
    }

    /** The finished assets made from a raw item, with their timelines and the clips of that raw item they use. */
    public ContentViews.RawLineageView lineage(CurrentPerson viewer, UUID rawItemId) throws SQLException {
        if (!AssetAccess.mayRead(viewer)) {
            throw rawNotFound();
        }
        return transactions.inTransaction(connection -> {
            content.findRawItem(connection, rawItemId).orElseThrow(() -> rawNotFound());
            Map<UUID, List<LineageRow>> byAsset = new LinkedHashMap<>();
            for (LineageRow row : content.lineageOf(connection, rawItemId)) {
                byAsset.computeIfAbsent(row.assetId(), id -> new ArrayList<>()).add(row);
            }
            List<ContentViews.LineageAssetView> assets = new ArrayList<>();
            for (List<LineageRow> rows : byAsset.values()) {
                LineageRow first = rows.get(0);
                List<UUID> clips = rows.stream().map(LineageRow::clipId).distinct().sorted().toList();
                assets.add(new ContentViews.LineageAssetView(first.assetId(), first.assetTitle(), first.timelineId(), clips));
            }
            return new ContentViews.RawLineageView(rawItemId, assets);
        });
    }

    /** Converts the request filter into the repository filter; "mine" becomes the caller's own identifier. */
    PoolFilter filter(CurrentPerson viewer, PoolQuery query) {
        if (query == null) {
            return PoolFilter.NONE;
        }
        UUID uploader = query.mine() ? viewer.personId() : query.uploaderId();
        Stage stage = query.stage() == null ? null : Stage.parse(query.stage());
        String usage = query.usage();
        if (usage != null && !usage.equals("used") && !usage.equals("never_used")) {
            throw new ProblemException(400, "Invalid filter", "The field 'usage' must be used or never_used.");
        }
        String tag = query.tag() == null ? null : FreeTag.parse(query.tag());
        return new PoolFilter(uploader, query.personId(), tag, stage, usage, query.from(), query.to());
    }

    private ContentViews.PoolItemView poolItem(PoolRow row, ItemTags tags) {
        ItemTags known = tags == null ? new ItemTags(Set.of(), Set.of()) : tags;
        String usage = "clip".equals(row.kind()) ? (row.used() ? "used" : "never_used") : null;
        return new ContentViews.PoolItemView(row.id(), row.kind(), row.stage().value(), row.status().value(), row.uploaderId(),
                row.title(), row.rawItemId(), row.assetId(), row.startSeconds(), row.endSeconds(), usage,
                List.copyOf(new TreeSet<>(known.personTags())), List.copyOf(new TreeSet<>(known.freeTags())), row.createdAt());
    }

    static ContentViews.ClipView clipView(ClipRecord clip, Set<String> freeTags) {
        return new ContentViews.ClipView(clip.id(), clip.rawItemId(), clip.uploaderId(), clip.title(),
                clip.range().startSeconds(), clip.range().endSeconds(), clip.status().value(), clip.stage().value(),
                List.copyOf(new TreeSet<>(freeTags)), clip.createdAt());
    }

    private static ContentViews.ItemTagsView tagsView(UUID item, ItemTags tags) {
        return new ContentViews.ItemTagsView(item, List.copyOf(new TreeSet<>(tags.freeTags())),
                List.copyOf(new TreeSet<>(tags.personTags())));
    }

    /** Refuses a tag change for an item that is neither a raw item nor a clip. */
    private void requireItem(Connection connection, UUID item) throws SQLException {
        if (content.findRawItem(connection, item).isEmpty() && content.findClip(connection, item).isEmpty()) {
            throw new ProblemException(404, "Not Found", "No item with this id.");
        }
    }

    static void requireEditor(CurrentPerson viewer) {
        if (!viewer.roles().contains(Role.EDITOR)) {
            throw new ProblemException(403, "Forbidden", "Only the editor role may change the content library.");
        }
    }

    static String requireTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE_LENGTH) {
            throw new ProblemException(400, "Invalid clip", "The field 'title' is required and must be at most "
                    + MAX_TITLE_LENGTH + " characters.");
        }
        return title.trim();
    }

    static int requireSeconds(String field, Integer value) {
        if (value == null) {
            throw new ProblemException(400, "Invalid clip", "The field '" + field + "' is required.");
        }
        return value;
    }

    private static ProblemException rawNotFound() {
        return new ProblemException(404, "Not Found", "No raw item with this id.");
    }

    private static ProblemException clipNotFound() {
        return new ProblemException(404, "Not Found", "No clip with this id.");
    }

    private static ProblemException invalidCursor() {
        return new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException invalid) {
            throw invalidCursor();
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw invalidCursor();
        }
    }
}
