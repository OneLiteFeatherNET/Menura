package net.onelitefeather.menura.core.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;

/** Applies the versioned SQL migrations of {@code db/migration} with Flyway. Re-running applies nothing new. */
public final class Migrations {

    static final String LOCATION = "classpath:db/migration";

    /**
     * The application schema. Flyway creates it if it is missing and keeps its history table
     * ({@code menura.flyway_schema_history}) there, so nothing lands in {@code public}.
     */
    static final String SCHEMA = "menura";

    private Migrations() {
    }

    /** Migrates the database behind a connection URL. Returns the number of migrations applied by this run. */
    public static int migrate(String url, String user, String password) {
        return configure(Flyway.configure().dataSource(url, user, password))
                .load()
                .migrate()
                .migrationsExecuted;
    }

    /** Migrates the database behind a pooled data source. Returns the number of migrations applied by this run. */
    public static int migrate(DataSource dataSource) {
        return configure(Flyway.configure().dataSource(dataSource))
                .load()
                .migrate()
                .migrationsExecuted;
    }

    private static FluentConfiguration configure(FluentConfiguration configuration) {
        return configuration.locations(LOCATION).schemas(SCHEMA).defaultSchema(SCHEMA).createSchemas(true);
    }
}
