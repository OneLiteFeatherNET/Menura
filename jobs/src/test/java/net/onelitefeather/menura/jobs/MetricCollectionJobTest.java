package net.onelitefeather.menura.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricCollectionJobTest {

    @Test
    void theJobIsSelectedByItsCronJobName() {
        assertEquals("metric-collection", MetricCollectionJob.NAME,
                "the Helm CronJob runs 'jobs run metric-collection'");
    }
}
