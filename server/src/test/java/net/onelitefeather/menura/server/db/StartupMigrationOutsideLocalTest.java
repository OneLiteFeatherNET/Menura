package net.onelitefeather.menura.server.db;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs in the default environment, which is not "local": the server must not migrate at startup. */
@MicronautTest(rebuildContext = true)
class StartupMigrationOutsideLocalTest {

    @Inject
    FakeMigrationRunner runner;

    @Test
    void serverDoesNotRunMigrationsAtStartupOutsideTheLocalProfile() {
        assertEquals(0, runner.runs(), "production startup must never run Flyway; the jobs migrate the schema");
    }
}
