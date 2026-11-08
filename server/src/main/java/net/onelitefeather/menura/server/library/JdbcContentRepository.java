package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.library.ClipRange;
import net.onelitefeather.menura.core.library.ClipStatus;
import net.onelitefeather.menura.core.library.Stage;
import net.onelitefeather.menura.core.library.TranscriptSegment;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The PostgreSQL store of the content library. Raw items are the verified raw-material rows of the asset table; the
 * pool and the search are single statements whose optional filters are bound as nulls when unused.
 */
@Singleton
public class JdbcContentRepository implements ContentRepository {

    private static final String CLIP_COLUMNS = "id, raw_item_id, uploader_id, title, start_seconds, end_seconds, status, "
            + "stage, created_at, updated_at";

    private static final String POOL = """
            with pool as (
              select a.id, 'raw' as kind, 'raw' as stage, 'active' as status, a.uploader_id, a.stream_title as title,
                     cast(null as uuid) as raw_item_id, cast(null as uuid) as asset_id,
                     cast(null as integer) as start_seconds, cast(null as integer) as end_seconds,
                     exists (select 1 from menura.clip c
                               join menura.lineage_edge e on e.parent_kind = 'clip' and e.parent_id = c.id
                              where c.raw_item_id = a.id) as used,
                     a.created_at
                from menura.asset a
               where a.kind = 'raw_material'
              union all
              select c.id, 'clip', c.stage, c.status, c.uploader_id, c.title, c.raw_item_id, cast(null as uuid),
                     c.start_seconds, c.end_seconds,
                     exists (select 1 from menura.lineage_edge e where e.parent_kind = 'clip' and e.parent_id = c.id),
                     c.created_at
                from menura.clip c
              union all
              select r.asset_id, 'clip', 'rendered', 'active', r.uploader_id, r.title, cast(null as uuid), r.asset_id,
                     cast(null as integer), cast(null as integer), false, r.registered_at
                from menura.registered_asset r
            )
            select p.id, p.kind, p.stage, p.status, p.uploader_id, p.title, p.raw_item_id, p.asset_id, p.start_seconds,
                   p.end_seconds, p.used, p.created_at
              from pool p
             where (cast(? as uuid) is null or p.uploader_id = cast(? as uuid))
               and (cast(? as uuid) is null or exists (select 1 from menura.item_person_tag t
                                                        where t.item_id = p.id and t.person_id = cast(? as uuid)))
               and (cast(? as text) is null or exists (select 1 from menura.item_tag t
                                                        where t.item_id = p.id and t.tag = cast(? as text)))
               and (cast(? as text) is null or p.stage = cast(? as text))
               and (cast(? as text) is null or p.used = (cast(? as text) = 'used'))
               and (cast(? as date) is null or cast(p.created_at at time zone 'UTC' as date) >= cast(? as date))
               and (cast(? as date) is null or cast(p.created_at at time zone 'UTC' as date) <= cast(? as date))
               and (cast(? as timestamptz) is null or (p.created_at, p.id) < (cast(? as timestamptz), cast(? as uuid)))
             order by p.created_at desc, p.id desc
             limit ?
            """;

    private static final String SEARCH = """
            select s.raw_item_id, a.stream_title, a.stream_date, a.uploader_id, s.start_ms, s.end_ms, s.speaker, s.text
              from menura.transcript_segment s
              join menura.asset a on a.id = s.raw_item_id
             where s.search @@ plainto_tsquery('simple', ?)
               and (cast(? as uuid) is null or a.uploader_id = cast(? as uuid))
               and (cast(? as uuid) is null or exists (select 1 from menura.item_person_tag t
                                                        where t.item_id = a.id and t.person_id = cast(? as uuid)))
               and (cast(? as text) is null or exists (select 1 from menura.item_tag t
                                                        where t.item_id = a.id and t.tag = cast(? as text)))
               and (cast(? as text) is null or cast(? as text) = 'raw' or exists (select 1 from menura.clip c
                                                        where c.raw_item_id = a.id and c.stage = cast(? as text)))
               and (cast(? as date) is null or a.stream_date >= cast(? as date))
               and (cast(? as date) is null or a.stream_date <= cast(? as date))
               and (cast(? as text) is null or exists (select 1 from menura.clip c
                                                        join menura.lineage_edge e
                                                          on e.parent_kind = 'clip' and e.parent_id = c.id
                                                       where c.raw_item_id = a.id) = (cast(? as text) = 'used'))
             order by a.stream_date desc nulls last, s.start_ms
             limit ?
            """;

    @Override
    public Optional<RawItem> findRawItem(Connection connection, UUID id) throws SQLException {
        String sql = "select id, uploader_id, stream_title, stream_date, duration_seconds, created_at from menura.asset "
                + "where id = ? and kind = 'raw_material'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                Date date = row.getDate("stream_date");
                return Optional.of(new RawItem(row.getObject("id", UUID.class), row.getObject("uploader_id", UUID.class),
                        row.getString("stream_title"), date == null ? null : date.toLocalDate(),
                        row.getInt("duration_seconds"), row.getTimestamp("created_at").toInstant()));
            }
        }
    }

    @Override
    public Optional<ClipRecord> findClip(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select " + CLIP_COLUMNS + " from menura.clip where id = ?")) {
            statement.setObject(1, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readClip(row)) : Optional.empty();
            }
        }
    }

    @Override
    public void insertClip(Connection connection, ClipRecord clip) throws SQLException {
        String sql = "insert into menura.clip (" + CLIP_COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, clip.id());
            statement.setObject(2, clip.rawItemId());
            statement.setObject(3, clip.uploaderId());
            statement.setString(4, clip.title());
            statement.setInt(5, clip.range().startSeconds());
            statement.setInt(6, clip.range().endSeconds());
            statement.setString(7, clip.status().value());
            statement.setString(8, clip.stage().value());
            statement.setTimestamp(9, Timestamp.from(clip.createdAt()));
            statement.setTimestamp(10, Timestamp.from(clip.updatedAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public void updateClip(Connection connection, ClipRecord clip) throws SQLException {
        String sql = "update menura.clip set title = ?, status = ?, stage = ?, updated_at = ? where id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, clip.title());
            statement.setString(2, clip.status().value());
            statement.setString(3, clip.stage().value());
            statement.setTimestamp(4, Timestamp.from(clip.updatedAt()));
            statement.setObject(5, clip.id());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteClip(Connection connection, UUID id) throws SQLException {
        replaceTags(connection, id, new ItemTags(Set.of(), Set.of()));
        try (PreparedStatement statement = connection.prepareStatement("delete from menura.clip where id = ?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    @Override
    public Map<UUID, ItemTags> tagsOf(Connection connection, Collection<UUID> items) throws SQLException {
        Map<UUID, Set<String>> free = new HashMap<>();
        Map<UUID, Set<UUID>> persons = new HashMap<>();
        if (!items.isEmpty()) {
            UUID[] ids = items.toArray(UUID[]::new);
            try (PreparedStatement statement = connection.prepareStatement(
                    "select item_id, tag from menura.item_tag where item_id = any(?)")) {
                statement.setArray(1, connection.createArrayOf("uuid", ids));
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        free.computeIfAbsent(row.getObject("item_id", UUID.class), id -> new HashSet<>())
                                .add(row.getString("tag"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "select item_id, person_id from menura.item_person_tag where item_id = any(?)")) {
                statement.setArray(1, connection.createArrayOf("uuid", ids));
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        persons.computeIfAbsent(row.getObject("item_id", UUID.class), id -> new HashSet<>())
                                .add(row.getObject("person_id", UUID.class));
                    }
                }
            }
        }
        Map<UUID, ItemTags> result = new HashMap<>();
        for (UUID item : items) {
            result.put(item, new ItemTags(free.getOrDefault(item, Set.of()), persons.getOrDefault(item, Set.of())));
        }
        return result;
    }

    @Override
    public void replaceTags(Connection connection, UUID item, ItemTags tags) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from menura.item_tag where item_id = ?")) {
            statement.setObject(1, item);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.item_tag (item_id, tag) values (?, ?)")) {
            for (String tag : tags.freeTags()) {
                statement.setObject(1, item);
                statement.setString(2, tag);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from menura.item_person_tag where item_id = ?")) {
            statement.setObject(1, item);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into menura.item_person_tag (item_id, person_id) values (?, ?)")) {
            for (UUID person : tags.personTags()) {
                statement.setObject(1, item);
                statement.setObject(2, person);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Override
    public List<PoolRow> pool(Connection connection, PoolFilter filter, Instant afterCreatedAt, UUID afterId, int limit)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(POOL)) {
            int at = 1;
            at = bindUuid(statement, at, filter.uploaderId(), 2);
            at = bindUuid(statement, at, filter.personId(), 2);
            at = bindText(statement, at, filter.tag(), 2);
            at = bindText(statement, at, filter.stage() == null ? null : filter.stage().value(), 2);
            at = bindText(statement, at, filter.usage(), 2);
            at = bindDate(statement, at, filter.from(), 2);
            at = bindDate(statement, at, filter.to(), 2);
            Timestamp after = afterCreatedAt == null ? null : Timestamp.from(afterCreatedAt);
            statement.setTimestamp(at++, after);
            statement.setTimestamp(at++, after);
            statement.setObject(at++, afterId);
            statement.setInt(at, limit);
            List<PoolRow> rows = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    rows.add(readPoolRow(row));
                }
            }
            return rows;
        }
    }

    @Override
    public boolean hasTranscript(Connection connection, UUID rawItem) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from menura.transcript where raw_item_id = ?")) {
            statement.setObject(1, rawItem);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    @Override
    public boolean replaceTranscript(Connection connection, UUID rawItem, String format, UUID uploaderId, Instant uploadedAt,
                                     List<TranscriptSegment> segments) throws SQLException {
        boolean existed = hasTranscript(connection, rawItem);
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into menura.transcript (raw_item_id, format, uploader_id, uploaded_at) values (?, ?, ?, ?)
                on conflict (raw_item_id) do update set format = excluded.format,
                    uploader_id = excluded.uploader_id, uploaded_at = excluded.uploaded_at
                """)) {
            statement.setObject(1, rawItem);
            statement.setString(2, format);
            statement.setObject(3, uploaderId);
            statement.setTimestamp(4, Timestamp.from(uploadedAt));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from menura.transcript_segment where raw_item_id = ?")) {
            statement.setObject(1, rawItem);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into menura.transcript_segment (raw_item_id, start_ms, end_ms, speaker, text)
                values (?, ?, ?, ?, ?)
                """)) {
            for (TranscriptSegment segment : segments) {
                statement.setObject(1, rawItem);
                statement.setLong(2, segment.startMillis());
                statement.setLong(3, segment.endMillis());
                statement.setString(4, segment.speaker());
                statement.setString(5, segment.text());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return existed;
    }

    @Override
    public List<SearchHit> search(Connection connection, String query, PoolFilter filter, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SEARCH)) {
            statement.setString(1, query);
            int at = 2;
            at = bindUuid(statement, at, filter.uploaderId(), 2);
            at = bindUuid(statement, at, filter.personId(), 2);
            at = bindText(statement, at, filter.tag(), 2);
            String stage = filter.stage() == null ? null : filter.stage().value();
            at = bindText(statement, at, stage, 3);
            at = bindDate(statement, at, filter.from(), 2);
            at = bindDate(statement, at, filter.to(), 2);
            at = bindText(statement, at, filter.usage(), 2);
            statement.setInt(at, limit);
            List<SearchHit> hits = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    Date date = row.getDate("stream_date");
                    TranscriptSegment segment = new TranscriptSegment(row.getLong("start_ms"), row.getLong("end_ms"),
                            row.getString("speaker"), row.getString("text"));
                    hits.add(new SearchHit(row.getObject("raw_item_id", UUID.class), row.getString("stream_title"),
                            date == null ? null : date.toLocalDate(), row.getObject("uploader_id", UUID.class), segment));
                }
            }
            return hits;
        }
    }

    @Override
    public void insertTimeline(Connection connection, TimelineRecord timeline) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into menura.timeline (id, uploader_id, title, document, created_at)
                values (?, ?, ?, cast(? as jsonb), ?)
                """)) {
            statement.setObject(1, timeline.id());
            statement.setObject(2, timeline.uploaderId());
            statement.setString(3, timeline.title());
            statement.setString(4, timeline.documentJson());
            statement.setTimestamp(5, Timestamp.from(timeline.createdAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<TimelineRecord> findTimeline(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, uploader_id, title, cast(document as text) as document, created_at from menura.timeline where id = ?")) {
            statement.setObject(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new TimelineRecord(row.getObject("id", UUID.class), row.getObject("uploader_id", UUID.class),
                        row.getString("title"), row.getString("document"), row.getTimestamp("created_at").toInstant()));
            }
        }
    }

    @Override
    public void insertEdge(Connection connection, String childKind, UUID childId, String parentKind, UUID parentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into menura.lineage_edge (child_kind, child_id, parent_kind, parent_id) values (?, ?, ?, ?)
                on conflict do nothing
                """)) {
            statement.setString(1, childKind);
            statement.setObject(2, childId);
            statement.setString(3, parentKind);
            statement.setObject(4, parentId);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean isClipUsed(Connection connection, UUID clipId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select exists (select 1 from menura.lineage_edge where parent_kind = 'clip' and parent_id = ?)
                """)) {
            statement.setObject(1, clipId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }

    @Override
    public Set<UUID> clipsOf(Connection connection, UUID timelineId) throws SQLException {
        Set<UUID> clips = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select parent_id from menura.lineage_edge where child_kind = 'timeline' and child_id = ? and parent_kind = 'clip'
                """)) {
            statement.setObject(1, timelineId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    clips.add(row.getObject(1, UUID.class));
                }
            }
        }
        return clips;
    }

    @Override
    public List<LineageRow> lineageOf(Connection connection, UUID rawItem) throws SQLException {
        List<LineageRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                select c.raw_item_id, ra.asset_id, ra.title, ae.parent_id as timeline_id, te.parent_id as clip_id
                  from menura.registered_asset ra
                  join menura.lineage_edge ae
                    on ae.child_kind = 'asset' and ae.child_id = ra.asset_id and ae.parent_kind = 'timeline'
                  join menura.lineage_edge te
                    on te.child_kind = 'timeline' and te.child_id = ae.parent_id and te.parent_kind = 'clip'
                  join menura.clip c on c.id = te.parent_id
                 where c.raw_item_id = ?
                 order by ra.asset_id, te.parent_id
                """)) {
            statement.setObject(1, rawItem);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    rows.add(new LineageRow(row.getObject("raw_item_id", UUID.class), row.getObject("asset_id", UUID.class),
                            row.getString("title"), row.getObject("timeline_id", UUID.class),
                            row.getObject("clip_id", UUID.class)));
                }
            }
        }
        return rows;
    }

    @Override
    public Optional<RegisteredAsset> findRegistration(Connection connection, UUID assetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select asset_id, uploader_id, title, timeline_id, registered_at from menura.registered_asset where asset_id = ?")) {
            statement.setObject(1, assetId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RegisteredAsset(row.getObject("asset_id", UUID.class),
                        row.getObject("uploader_id", UUID.class), row.getString("title"),
                        row.getObject("timeline_id", UUID.class), row.getTimestamp("registered_at").toInstant()));
            }
        }
    }

    @Override
    public void insertRegistration(Connection connection, RegisteredAsset registration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into menura.registered_asset (asset_id, uploader_id, title, timeline_id, registered_at)
                values (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, registration.assetId());
            statement.setObject(2, registration.uploaderId());
            statement.setString(3, registration.title());
            statement.setObject(4, registration.timelineId());
            statement.setTimestamp(5, Timestamp.from(registration.registeredAt()));
            statement.executeUpdate();
        }
    }

    private static ClipRecord readClip(ResultSet row) throws SQLException {
        return new ClipRecord(row.getObject("id", UUID.class), row.getObject("raw_item_id", UUID.class),
                row.getObject("uploader_id", UUID.class), row.getString("title"),
                ClipRange.of(row.getInt("start_seconds"), row.getInt("end_seconds")),
                ClipStatus.parse(row.getString("status")), Stage.parse(row.getString("stage")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }

    private static PoolRow readPoolRow(ResultSet row) throws SQLException {
        int start = row.getInt("start_seconds");
        Integer startSeconds = row.wasNull() ? null : start;
        int end = row.getInt("end_seconds");
        Integer endSeconds = row.wasNull() ? null : end;
        return new PoolRow(row.getObject("id", UUID.class), row.getString("kind"), Stage.parse(row.getString("stage")),
                ClipStatus.parse(row.getString("status")), row.getObject("uploader_id", UUID.class),
                row.getString("title"), row.getObject("raw_item_id", UUID.class), row.getObject("asset_id", UUID.class),
                startSeconds, endSeconds, row.getBoolean("used"), row.getTimestamp("created_at").toInstant());
    }

    /** Binds a nullable uuid as {@code times} parameters, starting at {@code at}; returns the next position. */
    private static int bindUuid(PreparedStatement statement, int at, UUID value, int times) throws SQLException {
        int next = at;
        for (int i = 0; i < times; i++) {
            statement.setObject(next++, value, Types.OTHER);
        }
        return next;
    }

    private static int bindText(PreparedStatement statement, int at, String value, int times) throws SQLException {
        int next = at;
        for (int i = 0; i < times; i++) {
            statement.setString(next++, value);
        }
        return next;
    }

    private static int bindDate(PreparedStatement statement, int at, LocalDate value, int times) throws SQLException {
        int next = at;
        for (int i = 0; i < times; i++) {
            statement.setObject(next++, value == null ? null : Date.valueOf(value), Types.DATE);
        }
        return next;
    }
}
