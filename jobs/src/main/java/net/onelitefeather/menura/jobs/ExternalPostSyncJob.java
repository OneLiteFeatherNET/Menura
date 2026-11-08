package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.channel.ChannelAudit;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.JdbcChannelStore;
import net.onelitefeather.menura.core.channel.JdkHttpTransport;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.config.ChannelSettings;
import net.onelitefeather.menura.core.config.MenuraSettings;
import net.onelitefeather.menura.core.db.DataSourceTransactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPostService;
import net.onelitefeather.menura.core.post.JdbcExternalPostStore;
import net.onelitefeather.menura.core.series.BrandTokens;
import net.onelitefeather.menura.core.series.JdbcSeriesStore;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.series.SeriesPlaylistCheck;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.TokenCipher;
import org.postgresql.ds.PGSimpleDataSource;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Imports the existing posts of connected channels and re-syncs them on a schedule (change add-external-post-import).
 * A channel whose last complete sync is newer than the interval is skipped; a channel that needs reconnection is
 * counted as waiting. Runs as a CronJob and prints counts only: no post title, token or secret is printed.
 */
public final class ExternalPostSyncJob implements Job {

    public static final String NAME = "external-post-sync";
    public static final String INTERVAL_VARIABLE = "MENURA_EXTERNAL_POST_SYNC_INTERVAL";
    public static final Duration DEFAULT_INTERVAL = Duration.ofHours(6);

    private final MenuraSettings database;
    private final ChannelSettings channels;
    private final Clock clock;
    private final Duration interval;

    public ExternalPostSyncJob(MenuraSettings database, ChannelSettings channels, Clock clock, Duration interval) {
        this.database = Objects.requireNonNull(database, "database");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.interval = Objects.requireNonNull(interval, "interval");
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

        YouTubeAdapter youtube = new YouTubeAdapter(clock,
                new YouTubeAdapter.Settings(channels.youtubeClientId(), channels.youtubeClientSecret(),
                        channels.youtubeRedirectUri()),
                new JdkHttpTransport());
        PlatformRegistry registry = new PlatformRegistry(List.of(youtube));
        SecureRandom random = new SecureRandom();
        DataSourceTransactions transactions = new DataSourceTransactions(dataSource);
        JdbcChannelStore channelStore = new JdbcChannelStore(cipher);
        ConnectionService connections = new ConnectionService(transactions, clock, new IdentifierGenerator(random),
                new SecretGenerator(random), channelStore, NO_AUDIT, registry, channels.refreshMargin(), 0.8);
        JdbcExternalPostStore posts = new JdbcExternalPostStore();
        JdbcSeriesStore seriesStore = new JdbcSeriesStore();
        SeriesService series = new SeriesService(clock, new IdentifierGenerator(random), seriesStore, posts, channelStore,
                NO_BRAND_KIT, JoinDomain.NONE);
        ExternalPostService service = new ExternalPostService(transactions, clock, new IdentifierGenerator(random),
                channelStore, posts, connections, registry, ExternalPostService.LISTING_LIMIT, series);

        ExternalPostService.SyncReport report = service.syncDue(interval);
        System.out.println("external-post-sync checked=" + report.checked() + " synced=" + report.synced()
                + " skipped=" + report.skipped() + " incomplete=" + report.incomplete() + " failed=" + report.failed()
                + " waiting=" + report.waiting());

        SeriesPlaylistCheck.Report playlists = new SeriesPlaylistCheck(transactions, clock, seriesStore, posts,
                channelStore, connections, registry).run();
        System.out.println("series-playlist-check checked=" + playlists.checked() + " missing=" + playlists.missing()
                + " notChecked=" + playlists.notChecked());
    }

    /**
     * The sync only assigns imported posts and checks playlists, which read no brand colour; a brand-kit read here would be a
     * programming error.
     */
    private static final BrandTokens NO_BRAND_KIT = new BrandTokens() {
        @Override
        public Optional<ColourToken> colour(String name) {
            throw new IllegalStateException("the external post sync does not read the brand kit");
        }

        @Override
        public boolean hasTemplate(String format) {
            throw new IllegalStateException("the external post sync does not read the brand kit");
        }
    };

    /** A sync acts for no person, so it writes no audit entry; reaching this would be a programming error. */
    private static final ChannelAudit NO_AUDIT = (connection, actor, action, target) -> {
        throw new IllegalStateException("the external post sync writes no audit entries, but was asked for " + action);
    };

    /** The interval from the environment, or the default of six hours. */
    public static Duration intervalFrom(java.util.Map<String, String> environment) {
        String value = environment.get(INTERVAL_VARIABLE);
        return value == null || value.isBlank() ? DEFAULT_INTERVAL : Duration.parse(value);
    }
}
