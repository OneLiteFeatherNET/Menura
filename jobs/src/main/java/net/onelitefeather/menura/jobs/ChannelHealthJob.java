package net.onelitefeather.menura.jobs;

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
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.TokenCipher;
import org.postgresql.ds.PGSimpleDataSource;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Refreshes the access tokens of connected channels before they expire, marks grants the platform rejected as needing
 * reconnection, and rewrites every grant with the primary encryption key. Runs as a CronJob. The job reports counts only;
 * no token and no secret is printed.
 *
 * <p>The token key is checked when the job runs, not when the dispatcher is built, so the other jobs start without it.
 */
public final class ChannelHealthJob implements Job {

    public static final String NAME = "channel-health";

    private final MenuraSettings database;
    private final ChannelSettings channels;
    private final Clock clock;

    public ChannelHealthJob(MenuraSettings database, ChannelSettings channels, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        SecureRandom random = new SecureRandom();
        ConnectionService service = new ConnectionService(new DataSourceTransactions(dataSource), clock,
                new IdentifierGenerator(random), new SecretGenerator(random), new JdbcChannelStore(cipher),
                NO_AUDIT, new PlatformRegistry(List.of(youtube)), channels.refreshMargin(), 0.8);

        ConnectionService.HealthReport report = service.checkHealth();
        System.out.println("channel-health checked=" + report.checked() + " refreshed=" + report.refreshed()
                + " needs_reconnection=" + report.needsReconnection() + " unavailable=" + report.unavailable());
    }

    /** A health run acts for no person, so it writes no audit entry; reaching this would be a programming error. */
    private static final ChannelAudit NO_AUDIT = (connection, actor, action, target) -> {
        throw new IllegalStateException("the channel health job writes no audit entries, but was asked for " + action);
    };
}
