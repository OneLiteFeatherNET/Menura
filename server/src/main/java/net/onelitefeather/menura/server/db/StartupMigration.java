package net.onelitefeather.menura.server.db;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;

/**
 * Migrates at startup only when the {@code local} environment is active, so a developer running the server
 * from the IDE gets a schema. Production and every other environment leave migrations to the jobs.
 */
@Singleton
@Requires(env = "local")
public class StartupMigration implements ApplicationEventListener<StartupEvent> {

    private final MigrationRunner runner;

    public StartupMigration(MigrationRunner runner) {
        this.runner = runner;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        runner.migrate();
    }
}
