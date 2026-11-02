package net.onelitefeather.menura.core.db;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/** Applies the versioned SQL migrations of {@code db/migration} with Flyway. Re-running applies nothing new. */
public final class Migrations {

    static final String LOCATION = "classpath:db/migration";

    private Migrations() {
    }

    /** Migrates the database behind a connection URL. Returns the number of migrations applied by this run. */
    public static int migrate(String url, String user, String password) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations(LOCATION)
                .load()
                .migrate()
                .migrationsExecuted;
    }

    /** Migrates the database behind a pooled data source. Returns the number of migrations applied by this run. */
    public static int migrate(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
