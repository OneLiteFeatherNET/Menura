package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.config.AnalyticsSettings;
import net.onelitefeather.menura.core.config.ChannelSettings;
import net.onelitefeather.menura.core.config.MenuraSettings;

import java.time.Clock;
import java.util.List;

/** Entry point of the jobs image: {@code jobs run <name>}. The exit code tells Kubernetes whether the run succeeded. */
public final class JobsMain {

    private JobsMain() {
    }

    public static void main(String[] args) {
        MenuraSettings settings = MenuraSettings.from(System.getenv());
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob(), new MigrateJob(settings),
                new ChannelHealthJob(settings, ChannelSettings.from(System.getenv()), Clock.systemUTC()),
                new ExternalPostSyncJob(settings, ChannelSettings.from(System.getenv()), Clock.systemUTC(),
                        ExternalPostSyncJob.intervalFrom(System.getenv())),
                new MetricCollectionJob(settings, ChannelSettings.from(System.getenv()), Clock.systemUTC(),
                        AnalyticsSettings.from(System.getenv())),
                new RetentionJob(System.getenv(), Clock.systemUTC()),
                new PublishDueJob(settings, ChannelSettings.from(System.getenv()), Clock.systemUTC(), System.getenv())));
        System.exit(dispatcher.dispatch(args, System.out, System.err));
    }
}
