package net.onelitefeather.menura.server.secret;

import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.JdbcAuditRepository;
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

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Feed tokens against a real PostgreSQL 17: only hashes are stored, and rotation revokes the previous row. */
@Tag("integration")
@Testcontainers
class CalendarFeedTokenIntegrationTest {

    private static final UUID PERSON = UUID.fromString("3f2e1d0c-b9a8-4f7e-8d6c-5b4a39281706");

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void storedRowHoldsOnlyTheHashOfTheSecret() throws SQLException {
        CalendarFeedTokens service = migratedService();

        String token = service.issue(new Caller(PERSON, Channel.WEB), "req-1");

        String secret = token.substring(token.indexOf('.') + 1);
        assertArrayEquals(SecretHasher.hash(secret), storedHashOf(PERSON),
                "the database must hold the SHA-256 of the secret and nothing that reveals the secret itself");
    }

    @Test
    void rotationLeavesOneActiveRowAndKeepsThePreviousRowRevoked() throws SQLException {
        CalendarFeedTokens service = migratedService();

        service.issue(new Caller(PERSON, Channel.WEB), "req-1");
        service.issue(new Caller(PERSON, Channel.WEB), "req-2");

        assertEquals(2, scalar("select count(*) from menura.calendar_feed_token where person_id = '" + PERSON + "'"));
        assertEquals(1, scalar("select count(*) from menura.calendar_feed_token where person_id = '" + PERSON
                + "' and revoked_at is null"), "exactly one token of a person may be active");
    }

    @Test
    void databaseRefusesASecondActiveTokenForOnePerson() throws SQLException {
        migratedService().issue(new Caller(PERSON, Channel.WEB), "req-1");

        assertThrows(SQLException.class, () -> {
            try (Connection connection = connection();
                 var statement = connection.prepareStatement(
                         "insert into menura.calendar_feed_token (id, person_id, secret_hash, created_at) values (?, ?, ?, now())")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, PERSON);
                statement.setBytes(3, new byte[32]);
                statement.executeUpdate();
            }
        }, "the partial unique index must refuse a second active row for the same person");
    }

    private CalendarFeedTokens migratedService() throws SQLException {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        SecureRandom random = new DeterministicRandom();
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        DataSourceTransactions transactions = new DataSourceTransactions(dataSource());
        AuditLog auditLog = new AuditLog(transactions, clock, new IdentifierGenerator(random), new JdbcAuditRepository());
        return new CalendarFeedTokens(transactions, clock, new IdentifierGenerator(random),
                new SecretGenerator(random), new JdbcCalendarFeedTokenRepository(), auditLog);
    }

    private byte[] storedHashOf(UUID person) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "select secret_hash from menura.calendar_feed_token where person_id = '" + person + "'")) {
            row.next();
            return row.getBytes(1);
        }
    }

    private PGSimpleDataSource dataSource() {
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
