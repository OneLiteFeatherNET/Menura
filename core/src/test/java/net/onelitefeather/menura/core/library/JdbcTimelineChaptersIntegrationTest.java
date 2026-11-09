package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.db.Migrations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chapter lookup of the publication against a real PostgreSQL 17 on a freshly migrated database: the marks of the
 * timeline a finished asset was registered from, read from the stored timeline document.
 */
@Tag("integration")
@Testcontainers
class JdbcTimelineChaptersIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final UUID UPLOADER = UUID.fromString("e0170000-0000-4000-8000-0000000000c1");
    private static final UUID ASSET = UUID.fromString("a55e0000-0000-4000-8000-0000000000c1");
    private static final UUID TIMELINE = UUID.fromString("71e10000-0000-4000-8000-0000000000c1");

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void theChaptersOfTheTimelineOfARegisteredAssetAreReturnedInPlaybackOrder() throws Exception {
        migrate();
        insertFinishedVideo(ASSET);
        insertTimeline(TIMELINE, "{\"sourceClipIds\":[],\"chapters\":[{\"seconds\":0,\"title\":\"Intro\"},"
                + "{\"seconds\":90,\"title\":\"Setup\"},{\"seconds\":300,\"title\":\"Demo\"}]}");
        insertRegistration(ASSET, TIMELINE);

        assertEquals(List.of(new ChapterMark(0, "Intro"), new ChapterMark(90, "Setup"), new ChapterMark(300, "Demo")),
                chaptersOf(ASSET), "the marks come back in the order of the timeline");
    }

    @Test
    void aTimelineWithoutChaptersGivesNoChapters() throws Exception {
        migrate();
        insertFinishedVideo(ASSET);
        insertTimeline(TIMELINE, "{\"sourceClipIds\":[],\"chapters\":[]}");
        insertRegistration(ASSET, TIMELINE);

        assertTrue(chaptersOf(ASSET).isEmpty(), "a timeline without marks gives no chapters");
    }

    @Test
    void aFinishedAssetThatWasNotRegisteredFromATimelineGivesNoChapters() throws Exception {
        migrate();
        insertFinishedVideo(ASSET);

        assertTrue(chaptersOf(ASSET).isEmpty(), "an asset without a registration gives no chapters");
    }

    private void migrate() throws SQLException {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private List<ChapterMark> chaptersOf(UUID asset) throws SQLException {
        try (Connection connection = connection()) {
            return new JdbcTimelineChapters().chaptersOf(connection, asset);
        }
    }

    private void insertFinishedVideo(UUID id) throws SQLException {
        execute("insert into menura.asset (id, uploader_id, kind, format, duration_seconds, width, height, size_bytes, "
                        + "checksum, storage_key, created_at) values (?, ?, 'video', 'mp4', 95, 1080, 1920, 1000000, ?, ?, ?)",
                id, UPLOADER, "checksum-" + id, "assets/" + id, Timestamp.from(NOW));
    }

    private void insertTimeline(UUID id, String document) throws SQLException {
        execute("insert into menura.timeline (id, uploader_id, title, document, created_at) "
                        + "values (?, ?, 'A timeline', cast(? as jsonb), ?)",
                id, UPLOADER, document, Timestamp.from(NOW));
    }

    private void insertRegistration(UUID asset, UUID timeline) throws SQLException {
        execute("insert into menura.registered_asset (asset_id, uploader_id, title, timeline_id, registered_at) "
                        + "values (?, ?, 'A video', ?, ?)",
                asset, UPLOADER, timeline, Timestamp.from(NOW));
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
