package net.onelitefeather.menura.core.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;

/** Applies the versioned SQL migrations of {@code db/migration} with Flyway. Re-running applies nothing new. */
public final class Migrations {

    static final String LOCATION = "classpath:db/migration";

    /**
     * The schema of Flyway's history table. Pinned, because without it Flyway uses the first schema on the role's
     * search path, which changes once the baseline has created the {@code menura} schema.
     */
    static final String HISTORY_SCHEMA = "public";

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
        return configuration.locations(LOCATION).defaultSchema(HISTORY_SCHEMA);
    }
}
