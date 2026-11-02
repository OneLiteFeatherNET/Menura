package net.onelitefeather.menura.jobs;

/** Does nothing and succeeds. It proves that the image, the entry point and the exit codes are wired. */
public final class PingJob implements Job {

    @Override
    public String name() {
        return "ping";
    }

    @Override
    public void execute() {
        // Intentionally empty.
    }
}
