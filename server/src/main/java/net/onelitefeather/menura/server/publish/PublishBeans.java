package net.onelitefeather.menura.server.publish;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.publish.ChannelQuotaService;
import net.onelitefeather.menura.core.publish.JdbcPostStore;
import net.onelitefeather.menura.core.publish.JdbcQuotaStore;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.core.publish.PostStore;
import net.onelitefeather.menura.core.publish.PublishSettings;
import net.onelitefeather.menura.core.publish.QuotaBudget;
import net.onelitefeather.menura.core.publish.QuotaLedger;
import net.onelitefeather.menura.core.series.SeriesService;

import java.time.Clock;

/**
 * Wires the content distribution (change add-content-distribution) into the server. The settings come from the
 * environment (see {@link PublishSettings}); the publication itself runs in the jobs component, not here.
 */
@Factory
public class PublishBeans {

    @Singleton
    PostStore postStore() {
        return new JdbcPostStore();
    }

    @Singleton
    QuotaLedger.Store quotaStore() {
        return new JdbcQuotaStore();
    }

    /** The remaining daily quota of a YouTube channel (change add-youtube-integration). */
    @Singleton
    ChannelQuotaService channelQuotaService(Transactions transactions, Clock clock, ChannelStore channels,
                                            QuotaLedger.Store store) {
        return new ChannelQuotaService(transactions, clock, channels, new QuotaLedger(QuotaBudget.youtubeDefaults(), store));
    }

    @Singleton
    PublishSettings publishSettings() {
        return PublishSettings.from(System.getenv());
    }

    @Singleton
    PostService postService(Transactions transactions, Clock clock, IdentifierGenerator identifiers, PostStore posts,
                            ChannelStore channels, SeriesService series, PlatformRegistry platforms,
                            ConnectionService connections, PublishSettings settings) {
        return new PostService(transactions, clock, identifiers, posts, channels, series, platforms, connections,
                settings.zone());
    }
}
