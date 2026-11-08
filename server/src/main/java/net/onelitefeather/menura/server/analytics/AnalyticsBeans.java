package net.onelitefeather.menura.server.analytics;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.analytics.AnalyticsReader;
import net.onelitefeather.menura.core.analytics.JdbcMetricStore;
import net.onelitefeather.menura.core.analytics.MetricStore;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.series.SeriesStore;

import java.time.Clock;

/** Wires the content analytics read models (change add-content-analytics) into the server. */
@Factory
public class AnalyticsBeans {

    @Singleton
    MetricStore metricStore(IdentifierGenerator identifiers) {
        return new JdbcMetricStore(identifiers);
    }

    @Singleton
    AnalyticsReader analyticsReader(Transactions transactions, Clock clock, MetricStore metrics,
                                    ExternalPostStore posts, ChannelStore channels, SeriesStore series) {
        return new AnalyticsReader(transactions, clock, metrics, posts, channels, series);
    }
}
