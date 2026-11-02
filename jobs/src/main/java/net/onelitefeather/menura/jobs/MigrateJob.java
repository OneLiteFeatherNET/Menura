package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.config.MenuraSettings;
import net.onelitefeather.menura.core.db.Migrations;

/**
 * Applies the database migrations. The Helm chart runs it as a hook before the server rolls out, and the
 * development compose start runs the same command. The server never migrates in production.
 */
public final class MigrateJob implements Job {

    private final MenuraSettings settings;

    public MigrateJob(MenuraSettings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "migrate";
    }

    @Override
    public void execute() {
        Migrations.migrate(settings.databaseUrl(), settings.databaseUser(), settings.databasePassword());
    }
}
