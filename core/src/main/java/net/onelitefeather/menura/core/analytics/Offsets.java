package net.onelitefeather.menura.core.analytics;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The fixed offsets after publication at which a post is snapshotted (change add-content-analytics): +1, +2, +4, +8,
 * +24, +48 hours and +7 days, then once per day from the eighth day up to {@link #MAX_DAY}.
 */
public final class Offsets {

    /** The last day with a daily snapshot. A bound on the work of one post; posts older than it keep their snapshots. */
    public static final int MAX_DAY = 365;

    static final List<Duration> FIXED = List.of(Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4),
            Duration.ofHours(8), Duration.ofHours(24), Duration.ofHours(48), Duration.ofDays(7));

    private static final int FIRST_DAILY_DAY = 8;

    private Offsets() {
    }

    /** The offsets of the fixed schedule, in order. */
    public static List<Duration> fixed() {
        return FIXED;
    }

    /**
     * The offsets a post has reached at the given age and has not yet been snapshotted at. {@code takenMinutes} holds the
     * offsets already stored, in minutes. A snapshot is due in the first collection at or after its offset, so every
     * offset not yet taken and not later than the age is due, in order.
     */
    public static List<Duration> due(Duration age, Set<Long> takenMinutes) {
        List<Duration> due = new ArrayList<>();
        for (Duration offset : FIXED) {
            if (offset.compareTo(age) <= 0 && !takenMinutes.contains(offset.toMinutes())) {
                due.add(offset);
            }
        }
        long days = Math.min(MAX_DAY, age.toDays());
        for (long day = FIRST_DAILY_DAY; day <= days; day++) {
            Duration offset = Duration.ofDays(day);
            if (!takenMinutes.contains(offset.toMinutes())) {
                due.add(offset);
            }
        }
        return due;
    }

    /**
     * True for an offset the comparison may use: one of the fixed offsets, or a whole number of days from the eighth day up
     * to {@link #MAX_DAY}.
     */
    public static boolean allowed(Duration offset) {
        if (FIXED.contains(offset)) {
            return true;
        }
        long days = offset.toDays();
        return days >= FIRST_DAILY_DAY && days <= MAX_DAY && offset.equals(Duration.ofDays(days));
    }
}
