package net.onelitefeather.menura.jobs;

import java.util.List;

/** Entry point of the jobs image: {@code jobs run <name>}. The exit code tells Kubernetes whether the run succeeded. */
public final class JobsMain {

    private JobsMain() {
    }

    public static void main(String[] args) {
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob()));
        System.exit(dispatcher.dispatch(args, System.out, System.err));
    }
}
