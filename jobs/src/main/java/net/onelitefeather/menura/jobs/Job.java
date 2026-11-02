package net.onelitefeather.menura.jobs;

/** A named unit of work that one Kubernetes CronJob or Job runs, selected with {@code jobs run <name>}. */
public interface Job {

    String name();

    /** Runs the job. Throwing an exception marks the run as failed. */
    void execute() throws Exception;
}
