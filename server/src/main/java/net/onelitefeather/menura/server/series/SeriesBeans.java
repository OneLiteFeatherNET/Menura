package net.onelitefeather.menura.server.series;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.series.BrandTokens;
import net.onelitefeather.menura.core.series.JdbcSeriesStore;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.core.series.SeriesStore;

import java.time.Clock;

/**
 * Wires the content series (change add-content-series) into the server. The join domain stays unconfigured until the
 * join attribution change provides one; the render bundle then has no call to action, and no error.
 */
@Factory
public class SeriesBeans {

    @Singleton
    SeriesStore seriesStore() {
        return new JdbcSeriesStore();
    }

    @Singleton
    SeriesService seriesService(Clock clock, IdentifierGenerator identifiers, SeriesStore series,
                                ExternalPostStore posts, ChannelStore channels, BrandTokens brand) {
        return new SeriesService(clock, identifiers, series, posts, channels, brand, JoinDomain.NONE);
    }
}
