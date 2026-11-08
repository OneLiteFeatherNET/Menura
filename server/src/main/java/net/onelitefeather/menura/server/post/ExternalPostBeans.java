package net.onelitefeather.menura.server.post;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPostService;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.series.SeriesService;

import java.time.Clock;

/** Wires the external post store and its service (change add-external-post-import) into the server. */
@Factory
public class ExternalPostBeans {

    @Singleton
    ExternalPostStore externalPostStore() {
        return new JdbcExternalPostStore();
    }

    @Singleton
    ExternalPostService externalPostService(Transactions transactions, Clock clock, IdentifierGenerator identifiers,
                                            ChannelStore channels, ExternalPostStore posts,
                                            ConnectionService connections, PlatformRegistry platforms,
                                            SeriesService series) {
        return new ExternalPostService(transactions, clock, identifiers, channels, posts, connections, platforms,
                ExternalPostService.LISTING_LIMIT, series);
    }
}
