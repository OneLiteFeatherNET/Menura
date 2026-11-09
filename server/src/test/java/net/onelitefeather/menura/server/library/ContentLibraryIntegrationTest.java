package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.library.retention.JdbcRawMediaCatalog;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.JdbcAuditRepository;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The content library against PostgreSQL 17 with every migration applied to an empty database. Each test seeds its own
 * people and items, and filters every query down to them, so the tests stay independent of each other and of order.
 * Exercises every SQL path of {@link JdbcContentRepository} and of the retention catalog.
 */
@Tag("integration")
@Testcontainers
class ContentLibraryIntegrationTest {

    private static final Instant CREATED = Instant.parse("2026-11-08T10:00:00Z");
    private static final long MIB = 1024L * 1024;
    private static final String TRANSCRIPT_WORD_PREFIX = "wort";

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    private Fixture fixture() {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        return new Fixture();
    }

    @Test
    void poolFiltersCombineUploaderPersonTagTagStageAndDateRange() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID guest = f.person("guest");
        UUID match = f.raw(uploader, "Treffer", CREATED, "2026-11-01", "available");
        UUID tooOld = f.raw(uploader, "Zu alt", Instant.parse("2025-01-01T00:00:00Z"), "2025-01-01", "available");
        f.raw(f.person("other"), "Anderer Uploader", CREATED, "2026-11-01", "available");
        f.library.setTags(f.editor(uploader), match, Set.of("speedrun"), Set.of(guest), "req-tags");

        List<ContentViews.PoolItemView> items = f.library.pool(f.viewer(uploader),
                new ContentLibrary.PoolQuery(false, uploader, guest, "speedrun", "raw", null,
                        LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")), null, 50).items();

        assertEquals(List.of(match), items.stream().map(ContentViews.PoolItemView::id).toList(),
                "uploader, person, tag, stage and date range select only the matching raw item");
        assertFalse(items.stream().anyMatch(item -> item.id().equals(tooOld)), "the item outside the date range is excluded");
    }

    @Test
    void usageFilterCoversRawItemsClipsAndRegisteredAssets() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID used = f.raw(uploader, "Benutzt", CREATED, "2026-11-01", "available");
        UUID unused = f.raw(uploader, "Ungenutzt", CREATED, "2026-11-01", "available");
        UUID clip = f.clip(uploader, used, "Schnitt");
        f.timelines.store(f.editor(uploader), "Kurz", document(clip), "req-tl");
        UUID video = f.finishedVideo(uploader, "Fertig");
        f.timelines.register(f.editor(uploader), video, timelineIdOf(f, uploader), "Fertig", "req-reg");

        List<ContentViews.PoolItemView> usedItems = f.library.pool(f.viewer(uploader),
                new ContentLibrary.PoolQuery(false, uploader, null, null, null, "used", null, null), null, 50).items();
        List<ContentViews.PoolItemView> unusedItems = f.library.pool(f.viewer(uploader),
                new ContentLibrary.PoolQuery(false, uploader, null, null, null, "never_used", null, null), null, 50).items();

        assertEquals(Set.of(used, clip), usedItems.stream().map(ContentViews.PoolItemView::id).collect(java.util.stream.Collectors.toSet()),
                "a raw item and its clip are used when a timeline refers to the clip");
        assertEquals(Set.of(unused, video), unusedItems.stream().map(ContentViews.PoolItemView::id)
                .collect(java.util.stream.Collectors.toSet()), "other raw items and finished assets are never used");
    }

    @Test
    void transcriptIsSearchableAndReturnsItsStartTimeAndFilters() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        String word = TRANSCRIPT_WORD_PREFIX + "suche";
        f.transcripts.upload(f.editor(uploader), raw, "WEBVTT\n\n00:01:02.000 --> 00:01:04.000\nWir spielen " + word + "\n",
                "req-t");

        List<ContentViews.SearchHitView> hits = f.library.search(f.viewer(uploader), word,
                ContentLibrary.PoolQuery.NONE, 20);
        List<ContentViews.SearchHitView> otherUploader = f.library.search(f.viewer(uploader), word,
                new ContentLibrary.PoolQuery(false, f.person("anderer"), null, null, null, null, null, null), 20);

        assertEquals(1, hits.size(), "the word is found once");
        assertEquals(raw, hits.get(0).rawItemId(), "the hit names its raw item");
        assertEquals(62_000, hits.get(0).startMillis(), "the hit opens at the start of the speech");
        assertTrue(otherUploader.isEmpty(), "the uploader filter applies to search");
        assertTrue(f.library.search(f.viewer(uploader), "unbekanntesWortXyz", ContentLibrary.PoolQuery.NONE, 20).isEmpty(),
                "a word that is not spoken matches nothing");
    }

    @Test
    void transcriptReplacementReplacesAllSegmentsAndWritesOneAuditEntry() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        f.transcripts.upload(f.editor(uploader), raw, "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\neins\n\n"
                + "00:00:03.000 --> 00:00:04.000\nzwei\n\n00:00:05.000 --> 00:00:06.000\ndrei\n", "req-1");

        ContentViews.TranscriptView replaced = f.transcripts.upload(f.editor(uploader), raw,
                "1\n00:00:01,000 --> 00:00:02,000\nneu\n\n2\n00:00:03,000 --> 00:00:04,000\nauch neu\n", "req-2");

        assertEquals(2, f.count("select count(*) from menura.transcript_segment where raw_item_id = ?", raw),
                "only the two new segments remain");
        assertTrue(replaced.replaced(), "the response says that an earlier transcript was replaced");
        assertEquals(1, f.count("select count(*) from menura.audit_log where target = ? and action = 'transcript.replace'",
                "raw_item:" + raw), "one audit entry records the replacement");
    }

    @Test
    void clipIsCreatedRenamedAndDeletedWithOneAuditEntryEach() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        UUID clip = f.clip(uploader, raw, "Erster Titel");

        f.library.renameClip(f.editor(f.person("redaktion")), clip, "Neuer Titel", "req-rename");
        f.library.deleteClip(f.editor(uploader), clip, "req-delete");

        assertEquals(0, f.count("select count(*) from menura.clip where id = ?", clip), "the deleted clip is gone");
        for (String action : List.of("clip.create", "clip.rename", "clip.delete")) {
            assertEquals(1, f.count("select count(*) from menura.audit_log where target = ? and action = ?",
                    "clip:" + clip, action), action + " is audited once");
        }
    }

    @Test
    void timelineRegistrationMovesClipsToRenderedAndRecordsLineage() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        UUID first = f.clip(uploader, raw, "Erster");
        UUID second = f.clip(uploader, raw, "Zweiter");
        UUID timeline = f.timelines.store(f.editor(uploader), "Kurz", document(first, second), "req-tl").id();
        UUID video = f.finishedVideo(uploader, "Kurz 9x16");

        f.timelines.register(f.editor(uploader), video, timeline, "Kurz 9x16", "req-reg");

        assertEquals(2, f.count("select count(*) from menura.clip where raw_item_id = ? and stage = 'rendered'", raw),
                "both clips are at stage rendered");
        List<ContentRepository.LineageRow> lineage = f.content.lineageOf(f.connection(), raw);
        assertEquals(2, lineage.size(), "the lineage names the asset with each of its clips");
        assertTrue(lineage.stream().allMatch(row -> row.assetId().equals(video) && row.timelineId().equals(timeline)),
                "every lineage row names the finished asset and its timeline");
        ContentViews.RawLineageView view = f.library.lineage(f.viewer(uploader), raw);
        assertEquals(List.of(video), view.assets().stream().map(ContentViews.LineageAssetView::assetId).toList(),
                "one finished asset is made from this raw item");
        assertTrue(f.library.pool(f.viewer(uploader), new ContentLibrary.PoolQuery(false, uploader, null, null, "rendered",
                null, null, null), null, 50).items().stream().anyMatch(item -> video.equals(item.assetId())),
                "the registered asset is listed in the pool at stage rendered");
    }

    @Test
    void clipUsedByTimelineCannotBeDeleted() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        UUID clip = f.clip(uploader, raw, "Benutzt");
        f.timelines.store(f.editor(uploader), "Kurz", document(clip), "req-tl");

        ProblemException refused = assertThrows(ProblemException.class,
                () -> f.library.deleteClip(f.editor(uploader), clip, "req-del"));

        assertEquals(409, refused.status(), "a clip that a timeline uses is kept");
        assertEquals(1, f.count("select count(*) from menura.clip where id = ?", clip), "the clip row remains");
    }

    @Test
    void unknownPersonTagIsRefusedAgainstThePersonTable() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");

        ProblemException refused = assertThrows(ProblemException.class, () -> f.library.setTags(f.editor(uploader), raw,
                Set.of(), Set.of(UUID.fromString("c3d4e5f6-0a1b-4c2d-8e3f-405162738495")), "req-tags"));

        assertEquals(400, refused.status(), "a person missing from the person table is a validation error");
        assertTrue(refused.detail().contains("personTags"), "the problem names the field");
    }

    @Test
    void explicitRawMediaRemovalKeepsRowLineageAndWritesOneAuditEntry() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        UUID clip = f.clip(uploader, raw, "Schnitt");
        UUID timeline = f.timelines.store(f.editor(uploader), "Kurz", document(clip), "req-tl").id();
        f.timelines.register(f.editor(uploader), f.finishedVideo(uploader, "Fertig"), timeline, "Fertig", "req-reg");

        f.rawMedia.removeMedia(f.editor(uploader), raw, "req-remove");

        assertEquals("removed", f.stringValue("select media_state from menura.asset where id = ?", raw),
                "the raw item keeps its row and records the removed media");
        assertEquals(1, f.library.lineage(f.viewer(uploader), raw).assets().size(), "lineage still names the asset");
        assertEquals(1, f.count("select count(*) from menura.audit_log where target = ? and action = 'raw_item.media_delete'",
                "raw_item:" + raw), "the removal is audited once");
    }

    @Test
    void retentionCatalogSelectsOnlyExpiredAvailableRawMediaAndMarksItCold() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID expired = f.raw(uploader, "Alt", Instant.parse("2025-01-01T00:00:00Z"), "2025-01-01", "available");
        UUID recent = f.raw(uploader, "Neu", CREATED, "2026-11-01", "available");
        JdbcRawMediaCatalog catalog = new JdbcRawMediaCatalog(f.tx);

        List<UUID> selected = catalog.olderThan(Instant.parse("2026-01-01T00:00:00Z"), 1000).stream()
                .map(ref -> ref.id()).toList();
        catalog.setMediaState(expired, "cold");

        assertTrue(selected.contains(expired), "the expired recording is selected");
        assertFalse(selected.contains(recent), "a recent recording is not selected");
        assertEquals("cold", f.stringValue("select media_state from menura.asset where id = ?", expired),
                "the item records its cold media state");
        assertTrue(catalog.olderThan(Instant.parse("2026-01-01T00:00:00Z"), 1000).stream()
                .noneMatch(ref -> ref.id().equals(expired)), "an item already cold is not selected again");
    }

    @Test
    void deletingAFinishedAssetRemovesItsPoolEntry() throws Exception {
        Fixture f = fixture();
        UUID uploader = f.person("uploader");
        UUID raw = f.raw(uploader, "Stream", CREATED, "2026-11-01", "available");
        UUID clip = f.clip(uploader, raw, "Schnitt");
        UUID timeline = f.timelines.store(f.editor(uploader), "Kurz", document(clip), "req-tl").id();
        UUID video = f.finishedVideo(uploader, "Fertig");
        f.timelines.register(f.editor(uploader), video, timeline, "Fertig", "req-reg");

        f.execute("delete from menura.asset where id = ?", video);

        assertEquals(0, f.count("select count(*) from menura.registered_asset where asset_id = ?", video),
                "the registration goes with the asset");
        assertTrue(f.library.pool(f.viewer(uploader), new ContentLibrary.PoolQuery(false, uploader, null, null, "rendered",
                null, null, null), null, 50).items().stream().noneMatch(item -> video.equals(item.assetId())),
                "the deleted asset is no longer in the pool");
    }

    private static UUID timelineIdOf(Fixture f, UUID uploader) throws SQLException {
        return f.uuidValue("select id from menura.timeline where uploader_id = ? order by created_at desc limit 1", uploader);
    }

    private static TimelineDocument document(UUID... clips) {
        return new TimelineDocument("short-9x16", List.of(clips), List.of(), List.of(), List.of());
    }

    /** One fixture per test: the services over the real PostgreSQL store, and the seeding helpers. */
    private final class Fixture {

        final Clock clock = Clock.systemUTC();
        final PGSimpleDataSource dataSource = dataSource();
        final DataSourceTransactions tx = new DataSourceTransactions(dataSource);
        final IdentifierGenerator identifiers = new IdentifierGenerator(new java.security.SecureRandom());
        final JdbcContentRepository content = new JdbcContentRepository();
        final AuditLog audit = new AuditLog(tx, clock, identifiers, new JdbcAuditRepository());
        final ContentLibrary library = new ContentLibrary(tx, clock, identifiers, content, audit);
        final Transcripts transcripts = new Transcripts(tx, clock, content, audit, 10 * MIB);
        final Timelines timelines = new Timelines(tx, clock, identifiers, content, new JdbcAssetRepository(), audit,
                ContentFixture.BRAND_KIT_TEMPLATES);
        final FakeObjectStorage storage = FakeObjectStorage.withClock(clock);
        final RawMedia rawMedia = new RawMedia(tx, content, storage, audit);

        private PGSimpleDataSource dataSource() {
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl());
            source.setUser(postgres.getUsername());
            source.setPassword(postgres.getPassword());
            return source;
        }

        CurrentPerson editor(UUID person) {
            return new CurrentPerson(person, "subject-" + person, Set.of(Role.EDITOR), Channel.WEB);
        }

        CurrentPerson viewer(UUID person) {
            return new CurrentPerson(person, "subject-" + person, Set.of(Role.VIEWER), Channel.WEB);
        }

        UUID person(String label) throws SQLException {
            UUID id = identifiers.next();
            execute("insert into person (id, issuer, subject, display_name, created_at) values (?, ?, ?, ?, ?)",
                    id, "https://issuer.test", id.toString(), label, Timestamp.from(CREATED));
            return id;
        }

        /** A raw item with its created_at, stream date and media state, inserted as the asset upload would. */
        UUID raw(UUID uploader, String title, Instant createdAt, String streamDate, String mediaState) throws SQLException {
            UUID id = identifiers.next();
            execute("insert into menura.asset (id, uploader_id, kind, format, duration_seconds, width, height, size_bytes, "
                            + "checksum, stream_title, stream_date, storage_key, created_at, media_state) "
                            + "values (?, ?, 'raw_material', 'mkv', 3600, 1920, 1080, ?, ?, ?, cast(? as date), ?, ?, ?)",
                    id, uploader, MIB, LibraryFixture.checksumFor(MIB, MIB), title, streamDate, "objects/" + id,
                    Timestamp.from(createdAt), mediaState);
            return id;
        }

        /** A verified finished video, inserted as the asset upload would; it is listed only once registered. */
        UUID finishedVideo(UUID uploader, String title) throws SQLException {
            UUID id = identifiers.next();
            execute("insert into menura.asset (id, uploader_id, kind, format, duration_seconds, width, height, size_bytes, "
                            + "checksum, storage_key, created_at) "
                            + "values (?, ?, 'video', 'mp4', 95, 1080, 1920, ?, ?, ?, ?)",
                    id, uploader, MIB, LibraryFixture.checksumFor(MIB, MIB), "objects/" + id, Timestamp.from(CREATED));
            return id;
        }

        UUID clip(UUID uploader, UUID raw, String title) throws Exception {
            return library.createClip(editor(uploader), new ContentLibrary.ClipRequest(raw, 10, 20, title, Set.of(), null),
                    "req-clip").id();
        }

        Connection connection() throws SQLException {
            return dataSource.getConnection();
        }

        void execute(String sql, Object... params) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                statement.executeUpdate();
            }
        }

        long count(String sql, Object... params) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return row.getLong(1);
                }
            }
        }

        String stringValue(String sql, Object... params) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return row.getString(1);
                }
            }
        }

        UUID uuidValue(String sql, Object... params) throws SQLException {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    return row.getObject(1, UUID.class);
                }
            }
        }

        private void bind(PreparedStatement statement, Object[] params) throws SQLException {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
        }
    }
}
