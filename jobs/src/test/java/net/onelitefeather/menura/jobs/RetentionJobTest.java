package net.onelitefeather.menura.jobs;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetentionJobTest {

    /** A database that is not running: the job must not connect to it while the policy is disabled. */
    private static Map<String, String> environment(String enabled, String batch) {
        return Map.of("MENURA_DB_URL", "jdbc:postgresql://127.0.0.1:1/menura", "MENURA_DB_USER", "menura",
                "MENURA_DB_PASSWORD", "unused", "MENURA_RETENTION_ENABLED", enabled, "MENURA_RETENTION_BATCH_SIZE", batch);
    }

    @Test
    void jobIsNamedRetentionForTheCronJobCommand() {
        assertEquals("retention", new RetentionJob(environment("false", "100"), Clock.systemUTC()).name());
    }

    @Test
    void disabledPolicyFinishesWithoutConnectingToTheDatabase() {
        RetentionJob job = new RetentionJob(environment("false", "100"), Clock.systemUTC());

        assertDoesNotThrow(job::execute, "a disabled policy is a successful, empty run");
    }

    @Test
    void invalidBatchSizeFailsTheRun() {
        RetentionJob job = new RetentionJob(environment("true", "0"), Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, job::execute, "a misconfigured policy fails the run");
    }
}
