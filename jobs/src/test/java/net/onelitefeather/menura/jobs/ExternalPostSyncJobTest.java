package net.onelitefeather.menura.jobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalPostSyncJobTest {

    @Test
    void theJobIsSelectedByItsCronJobName() {
        assertEquals("external-post-sync", ExternalPostSyncJob.NAME,
                "the Helm CronJob runs 'jobs run external-post-sync'");
    }

    @Test
    void theIntervalDefaultsToSixHoursWhenNoneIsSet() {
        assertEquals(Duration.ofHours(6), ExternalPostSyncJob.intervalFrom(Map.of()),
                "without MENURA_EXTERNAL_POST_SYNC_INTERVAL the sync runs every six hours");
    }

    @Test
    void theIntervalIsReadFromTheEnvironmentAsIso8601() {
        assertEquals(Duration.ofMinutes(90), ExternalPostSyncJob.intervalFrom(Map.of("MENURA_EXTERNAL_POST_SYNC_INTERVAL", "PT1H30M")),
                "the interval is an ISO-8601 duration");
    }
}
