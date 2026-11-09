package net.onelitefeather.menura.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricRollupJobTest {

    @Test
    void theJobIsSelectedByItsCronJobName() {
        assertEquals("metric-rollup", MetricRollupJob.NAME, "the Helm CronJob runs 'jobs run metric-rollup'");
    }
}
