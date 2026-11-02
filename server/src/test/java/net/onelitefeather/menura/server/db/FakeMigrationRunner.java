package net.onelitefeather.menura.server.db;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

/** Counts migration runs in memory instead of touching a database. Each test context gets its own instance. */
@Singleton
@Replaces(CoreMigrationRunner.class)
class FakeMigrationRunner implements MigrationRunner {

    private int runs;

    @Override
    public void migrate() {
        runs++;
    }

    int runs() {
        return runs;
    }
}
