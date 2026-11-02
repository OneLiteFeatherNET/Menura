package net.onelitefeather.menura.server.db;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** With the local profile enabled, the server migrates once at startup, for local development only. */
@MicronautTest(rebuildContext = true, environments = "local")
class StartupMigrationLocalTest {

    @Inject
    FakeMigrationRunner runner;

    @Test
    void localProfileRunsMigrationsOnceAtStartup() {
        assertEquals(1, runner.runs(), "the local profile must migrate at startup");
    }
}
