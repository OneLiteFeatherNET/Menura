package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.channel.ChannelAudit;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.JdkHttpTransport;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapters;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.config.ChannelSettings;
import net.onelitefeather.menura.core.config.MenuraSettings;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.library.JdbcTimelineChapters;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.publish.JdbcPostStore;
import net.onelitefeather.menura.core.publish.JdbcQuotaStore;
import net.onelitefeather.menura.core.publish.PublicationRunner;
import net.onelitefeather.menura.core.publish.PublishSettings;
import net.onelitefeather.menura.core.publish.QuotaBudget;
import net.onelitefeather.menura.core.publish.QuotaLedger;
import software.amazon.awssdk.services.s3.S3Client;
import net.onelitefeather.menura.core.series.BrandTokens;
import net.onelitefeather.menura.core.series.JdbcSeriesStore;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.TokenCipher;
import org.postgresql.ds.PGSimpleDataSource;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Publishes the due scheduled posts (change add-content-distribution, the CronJob {@code jobs run publish-due}). One run
 * claims the due posts with leases, publishes them or hands them to a natively scheduling platform, and records every
 * step and attempt. The CronJob has concurrency one, and a claim lease keeps two runs from publishing the same post.
 * The run prints counts only: no title, token, URL or secret is printed.
 */
public final class PublishDueJob implements Job {

    public static final String NAME = "publish-due";

    private final MenuraSettings database;
    private final ChannelSettings channels;
    private final Clock clock;
    private final Map<String, String> environment;

    public PublishDueJob(MenuraSettings database, ChannelSettings channels, Clock clock, Map<String, String> environment) {
        this.database = Objects.requireNonNull(database, "database");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.environment = Objects.requireNonNull(environment, "environment");
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
        JdbcExternalPostStore externalPosts = new JdbcExternalPostStore();
        SeriesService series = new SeriesService(clock, new IdentifierGenerator(random), new JdbcSeriesStore(), externalPosts,
                channelStore, NO_BRAND_KIT, JoinDomain.NONE);
        PublishSettings settings = PublishSettings.from(environment);

        try (S3Client s3 = S3MediaStorage.client(environment)) {
            PublicationRunner.Stepwise stepwise = new PublicationRunner.Stepwise(cipher,
                    new S3MediaStorage(s3, S3MediaStorage.bucket(environment)),
                    new QuotaLedger(QuotaBudget.youtubeDefaults(), new JdbcQuotaStore()), new JdbcTimelineChapters());
            PublicationRunner.Report report = new PublicationRunner(transactions, clock, new IdentifierGenerator(random),
                    new JdbcPostStore(), channelStore, connections, registry, externalPosts, series, settings, stepwise)
                    .runOnce();
            System.out.println("publish-due claimed=" + report.claimed() + " published=" + report.published()
                    + " handedOver=" + report.handedOver() + " confirmed=" + report.confirmed() + " retried=" + report.retried()
                    + " failed=" + report.failed() + " waiting=" + report.waiting());
        }
    }

    /** The publication never reads the brand kit; reaching it would be a programming error. */
    private static final BrandTokens NO_BRAND_KIT = new BrandTokens() {
        @Override
        public Optional<net.onelitefeather.menura.core.brand.BrandKit.ColourToken> colour(String name) {
            throw new IllegalStateException("the publication job does not read the brand kit");
        }

        @Override
        public boolean hasTemplate(String format) {
            throw new IllegalStateException("the publication job does not read the brand kit");
        }
    };

    /** A publication run acts for no person and writes no audit entry; reaching this would be a programming error. */
    private static final ChannelAudit NO_AUDIT = (connection, actor, action, target) -> {
        throw new IllegalStateException("the publication job writes no audit entries, but was asked for " + action);
    };
}
