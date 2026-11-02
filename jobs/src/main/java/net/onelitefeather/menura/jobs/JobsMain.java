package net.onelitefeather.menura.jobs;

import net.onelitefeather.menura.core.config.MenuraSettings;

import java.util.List;

/** Entry point of the jobs image: {@code jobs run <name>}. The exit code tells Kubernetes whether the run succeeded. */
public final class JobsMain {

    private JobsMain() {
    }

    public static void main(String[] args) {
        MenuraSettings settings = MenuraSettings.from(System.getenv());
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob(), new MigrateJob(settings)));
        System.exit(dispatcher.dispatch(args, System.out, System.err));
    }
}
