package net.onelitefeather.menura.server.analytics;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.analytics.MetricStore;
import net.onelitefeather.menura.core.analytics.support.InMemoryMetricStore;

/**
 * Test-only replacement of the metric store: points, latest values and snapshots live in memory, so no test needs a
 * database. The tests inject the same instance to seed the values they read back over HTTP.
 */
@Factory
@Requires(env = Environment.TEST)
class AnalyticsTestFakes {

    @Singleton
    InMemoryMetricStore inMemoryMetricStore() {
        return new InMemoryMetricStore();
    }

    @Singleton
    @io.micronaut.context.annotation.Replaces(factory = AnalyticsBeans.class, bean = MetricStore.class)
    MetricStore metricStore(InMemoryMetricStore store) {
        return store;
    }
}
