package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.analytics.JdbcMetricStore;
import net.onelitefeather.menura.core.analytics.MetricCollector;
import net.onelitefeather.menura.core.analytics.PublishSlot;
import net.onelitefeather.menura.core.channel.ChannelAudit;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.JdkHttpTransport;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapters;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.config.AnalyticsSettings;
import net.onelitefeather.menura.core.config.ChannelSettings;
import net.onelitefeather.menura.core.config.MenuraSettings;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.TokenCipher;
import org.postgresql.ds.PGSimpleDataSource;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Collects the metrics of every external post of the connected channels (change add-content-analytics): the points, the
 * latest values and the snapshots at fixed offsets. Runs as a CronJob every half hour, so the +1 hour snapshot is at most
 * half an hour late. It prints counts only: no title, token or metric value of a post is printed.
 */
public final class MetricCollectionJob implements Job {

    public static final String NAME = "metric-collection";

    private final MenuraSettings database;
    private final ChannelSettings channels;
    private final Clock clock;
    private final AnalyticsSettings analytics;

    public MetricCollectionJob(MenuraSettings database, ChannelSettings channels, Clock clock,
                               AnalyticsSettings analytics) {
        this.database = Objects.requireNonNull(database, "database");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.analytics = Objects.requireNonNull(analytics, "analytics");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute() throws Exception {
        TokenCipher cipher = TokenCipher.of(channels.tokenKey(), channels.previousTokenKey(), new SecureRandom());
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(database.databaseUrl());
        dataSource.setUser(database.databaseUser());
        dataSource.setPassword(database.databasePassword());

        PlatformAdapter youtube = YouTubeAdapters.create(clock,
                new YouTubeAdapter.Settings(channels.youtubeClientId(), channels.youtubeClientSecret(),
                        channels.youtubeRedirectUri()),
                channels.youtubeMode(), new JdkHttpTransport());
        PlatformRegistry registry = new PlatformRegistry(List.of(youtube));
        SecureRandom random = new SecureRandom();
        DataSourceTransactions transactions = new DataSourceTransactions(dataSource);
        JdbcChannelStore channelStore = new JdbcChannelStore(cipher);
        ConnectionService connections = new ConnectionService(transactions, clock, new IdentifierGenerator(random),
                new SecretGenerator(random), channelStore, NO_AUDIT, registry, channels.refreshMargin(), 0.8);
        MetricCollector collector = new MetricCollector(transactions, clock,
                new JdbcMetricStore(new IdentifierGenerator(random), new PublishSlot(analytics.timeZone())), new JdbcExternalPostStore(), channelStore,
                connections, registry);

        MetricCollector.Report report = collector.collect();
        System.out.println("metric-collection channels=" + report.channels() + " collected=" + report.collected()
                + " notSupported=" + report.notSupported() + " failed=" + report.failed() + " waiting=" + report.waiting()
                + " points=" + report.points() + " snapshots=" + report.snapshots());
    }

    /** A collection acts for no person, so it writes no audit entry; reaching this would be a programming error. */
    private static final ChannelAudit NO_AUDIT = (connection, actor, action, target) -> {
        throw new IllegalStateException("the metric collection writes no audit entries, but was asked for " + action);
    };
}
