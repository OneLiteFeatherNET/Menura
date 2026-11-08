package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.db.Migrations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The library migration (V4) applies to an empty PostgreSQL 17 and creates its three tables. */
@Tag("integration")
@Testcontainers
class AssetMigrationIntegrationTest {

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    @Test
    void migrationCreatesTheAssetAndUploadSessionTables() throws Exception {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        try (Connection connection = source.getConnection();
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("select count(*) from information_schema.tables "
                     + "where table_schema = 'menura' and table_name in ('asset', 'upload_session', 'upload_session_part')")) {
            tables.next();
            assertEquals(3, tables.getInt(1), "the migration must create asset, upload_session and upload_session_part");
        }
    }
}
