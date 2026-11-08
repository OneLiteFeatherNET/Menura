package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;

/**
 * A time range of a raw item in whole seconds: the start is at least zero, the end is after the start. A clip is
 * only valid inside its recording, which {@link #within(int)} checks once the recording's duration is known.
 */
public record ClipRange(int startSeconds, int endSeconds) {

    public ClipRange {
        if (startSeconds < 0) {
            throw invalid("The clip must not start before the recording.");
        }
        if (endSeconds <= startSeconds) {
            throw invalid("The clip must end after it starts.");
        }
    }

    public static ClipRange of(int startSeconds, int endSeconds) {
        return new ClipRange(startSeconds, endSeconds);
    }

    /** Returns this range when it ends within a recording of the given duration, and refuses it otherwise. */
    public ClipRange within(int durationSeconds) {
        if (endSeconds > durationSeconds) {
            throw invalid("The clip ends at " + endSeconds + " seconds, after the recording of " + durationSeconds
                    + " seconds.");
        }
        return this;
    }

    private static ProblemException invalid(String detail) {
        return new ProblemException(400, "Invalid clip", detail);
    }
}
