package net.onelitefeather.menura.server.audit;

import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.db.DataSourceTransactions;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.support.DeterministicRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The audit entry and the write it describes commit or roll back together, against a real PostgreSQL 17. */
@Tag("integration")
@Testcontainers
class AuditLogIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID PERSON = UUID.fromString("9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d");

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void writeAndItsAuditEntryCommitTogether() throws SQLException {
        AuditLog auditLog = migratedAuditLog();

        auditLog.write(new Caller(PERSON, Channel.OBS), "calendar_feed_token.issue", "person:" + PERSON, "req-1",
                connection -> {
                    insertFeedTokenRow(connection, UUID.randomUUID());
                    return null;
                });

        assertEquals(1, scalar("select count(*) from menura.calendar_feed_token"), "the write must be committed");
        assertEquals(1, scalar("select count(*) from menura.audit_log"), "its audit entry must be committed");
    }

    @Test
    void auditEntryStoresPersonActionTargetTimeChannelAndRequestId() throws SQLException {
        AuditLog auditLog = migratedAuditLog();

        auditLog.write(new Caller(PERSON, Channel.MCP), "test.write", "probe", "req-2", connection -> null);

        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("select person_id, action, target, occurred_at, channel, request_id from menura.audit_log")) {
            assertEquals(true, row.next(), "one audit row must exist");
            assertEquals(PERSON, row.getObject("person_id", UUID.class));
            assertEquals("test.write", row.getString("action"));
            assertEquals("probe", row.getString("target"));
            assertEquals(NOW.toEpochMilli(), row.getTimestamp("occurred_at").toInstant().toEpochMilli(),
                    "the time must be the injected clock's, not the database's");
            assertEquals("mcp", row.getString("channel"));
            assertEquals("req-2", row.getString("request_id"));
        }
    }

    @Test
    void rolledBackWriteLeavesNoAuditEntryAndNoWrite() throws SQLException {
        AuditLog auditLog = migratedAuditLog();

        assertThrows(IllegalStateException.class, () -> auditLog.write(new Caller(PERSON, Channel.WEB),
                "calendar_feed_token.issue", "person:" + PERSON, "req-3", connection -> {
                    insertFeedTokenRow(connection, UUID.randomUUID());
                    throw new IllegalStateException("the write fails after its row was inserted");
                }));


        assertEquals(0, scalar("select count(*) from menura.calendar_feed_token"), "the rolled-back row must be gone");
        assertEquals(0, scalar("select count(*) from menura.audit_log"), "a rolled-back write must leave no audit entry");
    }

    private AuditLog migratedAuditLog() throws SQLException {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AuditRepository repository = new JdbcAuditRepository();
        return new AuditLog(new DataSourceTransactions(dataSource()), clock,
                new IdentifierGenerator(new DeterministicRandom()), repository);
    }

    private static void insertFeedTokenRow(Connection connection, UUID person) throws SQLException {
        try (var statement = connection.prepareStatement(
                "insert into menura.calendar_feed_token (id, person_id, secret_hash, created_at) values (?, ?, ?, now())")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, person);
            statement.setBytes(3, new byte[32]);
            statement.executeUpdate();
        }
    }

    private javax.sql.DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        return source;
    }

    private Connection connection() throws SQLException {
        return dataSource().getConnection();
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
