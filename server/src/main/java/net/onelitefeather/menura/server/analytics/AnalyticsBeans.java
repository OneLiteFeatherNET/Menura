package net.onelitefeather.menura.server.analytics;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.analytics.AnalyticsReader;
import net.onelitefeather.menura.core.analytics.JdbcMetricStore;
import net.onelitefeather.menura.core.analytics.MetricStore;
import net.onelitefeather.menura.core.analytics.PublishSlot;
import net.onelitefeather.menura.core.config.AnalyticsSettings;
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
    PublishSlot publishSlot() {
        return new PublishSlot(AnalyticsSettings.from(System.getenv()).timeZone());
    }

    @Singleton
    MetricStore metricStore(IdentifierGenerator identifiers, PublishSlot slots) {
        return new JdbcMetricStore(identifiers, slots);
    }

    @Singleton
    AnalyticsReader analyticsReader(Transactions transactions, Clock clock, PublishSlot slots, MetricStore metrics,
                                    ExternalPostStore posts, ChannelStore channels, SeriesStore series) {
        return new AnalyticsReader(transactions, clock, slots, metrics, posts, channels, series);
    }
}
