package net.onelitefeather.menura.jobs;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Dispatches {@code jobs run <name>} to the registered job of that name. */
public final class JobDispatcher {

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_USAGE = 2;

    static final String USAGE = "Usage: jobs run <name>";

    private final Map<String, Job> jobs = new TreeMap<>();

    public JobDispatcher(Collection<Job> registered) {
        for (Job job : registered) {
            jobs.put(job.name(), job);
        }
    }

    public int dispatch(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2 || !"run".equals(args[0])) {
            err.println(USAGE);
            err.println("Available jobs: " + String.join(", ", jobs.keySet()));
            return EXIT_USAGE;
        }
        String name = args[1];
        Job job = jobs.get(name);
        if (job == null) {
            err.println("Unknown job: " + name);
            err.println("Available jobs: " + String.join(", ", jobs.keySet()));
            return EXIT_USAGE;
        }
        try {
            job.execute();
            out.println("Job " + name + " finished.");
            return EXIT_OK;
        } catch (Exception e) {
            err.println("Job " + name + " failed: " + e.getMessage());
            return EXIT_FAILED;
        }
    }
}
