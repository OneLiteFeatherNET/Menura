package net.onelitefeather.menura.server.db;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Migrations;

import javax.sql.DataSource;

@Singleton
public class CoreMigrationRunner implements MigrationRunner {

    private final DataSource dataSource;

    public CoreMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void migrate() {
        Migrations.migrate(dataSource);
    }
}
