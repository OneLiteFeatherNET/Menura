package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.config.MenuraSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs the real migrate job against a fresh PostgreSQL 17 container. One container per test keeps tests independent. */
@Tag("integration")
@Testcontainers
class MigrateJobIntegrationTest {

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"));

    @Test
    void migrateJobCreatesTheMenuraSchemaInAnEmptyDatabase() throws SQLException {
        int exitCode = runMigrate();

        assertEquals(JobDispatcher.EXIT_OK, exitCode, "jobs run migrate must exit 0 on an empty database");
        assertEquals(1, scalar("select count(*) from information_schema.schemata where schema_name = 'menura'"),
                "the baseline migration must create the menura schema");
    }

    @Test
    void secondMigrateRunChangesNothing() throws SQLException {
        assertEquals(JobDispatcher.EXIT_OK, runMigrate(), "the first run must succeed");
        int historyAfterFirstRun = scalar("select count(*) from flyway_schema_history");

        int exitCode = runMigrate();

        assertEquals(JobDispatcher.EXIT_OK, exitCode, "a second run must also exit 0");
        assertEquals(historyAfterFirstRun, scalar("select count(*) from flyway_schema_history"),
                "a second run must not apply any migration again");
    }

    private int runMigrate() {
        MenuraSettings settings = MenuraSettings.from(Map.of(
                MenuraSettings.DATABASE_URL_VARIABLE, postgres.getJdbcUrl(),
                MenuraSettings.DATABASE_USER_VARIABLE, postgres.getUsername(),
                MenuraSettings.DATABASE_PASSWORD_VARIABLE, postgres.getPassword()));
        JobDispatcher dispatcher = new JobDispatcher(List.of(new MigrateJob(settings)));
        return dispatcher.dispatch(new String[]{"run", "migrate"}, discard(), discard());
    }

    private int scalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             java.sql.Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static PrintStream discard() {
        return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    }
}
