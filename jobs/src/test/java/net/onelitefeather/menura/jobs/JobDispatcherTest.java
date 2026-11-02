package net.onelitefeather.menura.jobs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobDispatcherTest {

    @Test
    void runPingExitsZero() {
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob()));

        int exitCode = dispatcher.dispatch(new String[]{"run", "ping"}, new Capture().stream(), new Capture().stream());

        assertEquals(JobDispatcher.EXIT_OK, exitCode, "jobs run ping must exit 0");
    }

    @Test
    void unknownJobExitsNonZeroAndNamesTheJob() {
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob()));
        Capture err = new Capture();

        int exitCode = dispatcher.dispatch(new String[]{"run", "nope"}, new Capture().stream(), err.stream());

        assertTrue(exitCode != JobDispatcher.EXIT_OK, "an unknown job must exit non-zero");
        assertTrue(err.text().contains("nope"), "the error must name the unknown job, was: " + err.text());
    }

    @Test
    void runWithoutJobNamePrintsUsageAndExitsNonZero() {
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob()));
        Capture err = new Capture();

        int exitCode = dispatcher.dispatch(new String[]{"run"}, new Capture().stream(), err.stream());

        assertTrue(exitCode != JobDispatcher.EXIT_OK, "running without a job name must exit non-zero");
        assertTrue(err.text().contains("Usage: jobs run <name>"), "usage must be printed, was: " + err.text());
    }

    @Test
    void noArgumentsPrintsUsageAndExitsNonZero() {
        JobDispatcher dispatcher = new JobDispatcher(List.of(new PingJob()));
        Capture err = new Capture();

        int exitCode = dispatcher.dispatch(new String[]{}, new Capture().stream(), err.stream());

        assertTrue(exitCode != JobDispatcher.EXIT_OK, "running without arguments must exit non-zero");
        assertTrue(err.text().contains("Usage: jobs run <name>"), "usage must be printed, was: " + err.text());
    }

    @Test
    void failingJobExitsOneAndReportsItsName() {
        Job failing = new Job() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public void execute() {
                throw new IllegalStateException("disk on fire");
            }
        };
        JobDispatcher dispatcher = new JobDispatcher(List.of(failing));
        Capture err = new Capture();

        int exitCode = dispatcher.dispatch(new String[]{"run", "broken"}, new Capture().stream(), err.stream());

        assertEquals(JobDispatcher.EXIT_FAILED, exitCode, "a job that throws must exit 1");
        assertTrue(err.text().contains("broken"), "the failure must name the job, was: " + err.text());
    }

    /** Collects what a command writes, without touching the process streams. */
    static final class Capture {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);

        PrintStream stream() {
            return stream;
        }

        String text() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
