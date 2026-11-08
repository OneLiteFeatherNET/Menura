package net.onelitefeather.menura.core.analytics.support;

import net.onelitefeather.menura.core.analytics.AnalyticsReader;
import net.onelitefeather.menura.core.analytics.MetricCollector;
import net.onelitefeather.menura.core.analytics.PublishSlot;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.DirectTransactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.support.InMemoryExternalPostStore;
import net.onelitefeather.menura.core.series.support.InMemorySeriesStore;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One fresh analytics world per test: a channel fixture (clock, channel store, randomness), a metrics platform, the post,
 * metric and series stores, and the collector and reader over them. No test shares any of it with another.
 */
public final class AnalyticsFixture {

    public final ChannelFixture channels = new ChannelFixture();
    public final MetricsPlatform platform;
    public final InMemoryExternalPostStore posts = new InMemoryExternalPostStore();
    public final InMemoryMetricStore metrics = new InMemoryMetricStore();
    public final InMemorySeriesStore series = new InMemorySeriesStore();
    public final PlatformRegistry registry;
    public final ConnectionService connections;
    public final MetricCollector collector;
    public final AnalyticsReader reader;

    /** A world whose platform reports metrics when {@code metricsSupported} is true. */
    public AnalyticsFixture(boolean metricsSupported) {
        this.platform = new MetricsPlatform(metricsSupported);
        this.registry = new PlatformRegistry(List.of(platform));
        this.connections = channels.service(channels.store, registry);
        this.collector = new MetricCollector(new DirectTransactions(), channels.clock, metrics, posts, channels.store,
                connections, registry);
        this.reader = new AnalyticsReader(new DirectTransactions(), channels.clock,
                new PublishSlot(ZoneId.of("Europe/Berlin")), metrics, posts, channels.store, series);
    }

    /** Stores a connected channel of the metrics platform with a grant that does not expire during the test. */
    public UUID connectedChannel(String account) {
        UUID id = UUID.nameUUIDFromBytes(("metrics-" + account).getBytes(StandardCharsets.UTF_8));
        channels.store.insertChannel(null, new Channel(id, MetricsPlatform.PLATFORM, account, "Channel " + account,
                ConnectionState.CONNECTED, null, Set.of(), Set.of("metrics.account"), null, ChannelFixture.NOW,
                ChannelFixture.NOW, null));
        channels.store.saveGrant(null, new ChannelGrant(id, "token-" + account, null,
                ChannelFixture.NOW.plus(Duration.ofDays(365)), ChannelFixture.NOW));
        return id;
    }

    /** Stores an external post of a channel published at the given time, as an import would. */
    public ExternalPost publishedPost(UUID channelId, String platformItemId, Instant publishedAt, String format)
            throws java.sql.SQLException {
        PublishedItem item = new PublishedItem(platformItemId, "https://video.example.test/" + platformItemId,
                "Title " + platformItemId, publishedAt, format);
        ExternalPost post = ExternalPost.create(UUID.nameUUIDFromBytes(("post-" + platformItemId)
                .getBytes(StandardCharsets.UTF_8)), channelId, MetricsPlatform.PLATFORM, item, ChannelFixture.NOW);
        posts.insert(null, post);
        return post;
    }

    /** Moves the injected clock forward. */
    public void advance(Duration duration) {
        channels.clock.advance(duration);
    }

    public Instant now() {
        return channels.clock.instant();
    }
}
