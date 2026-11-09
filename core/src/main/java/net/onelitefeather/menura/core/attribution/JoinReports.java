package net.onelitefeather.menura.core.attribution;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The read models of the join attribution: new players per period, and the estimates per post and per series. */
public final class JoinReports {

    /** The label every per-platform figure carries: the figure is what players stated, not what a platform measured. */
    public static final String STATED_LABEL = "stated by players";

    /** The label every post and series figure carries. */
    public static final String ESTIMATE_LABEL = "estimate";

    /** The name of the estimates of posts without a series. */
    public static final String NO_SERIES = "no series";

    private JoinReports() {
    }

    /** The period a report counts by: a calendar day, or an ISO week from Monday to Sunday in the installation's zone. */
    public enum Grouping {
        DAY, WEEK;

        public static Optional<Grouping> parse(String value) {
            if (value == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                return Optional.empty();
            }
        }

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The first joins of a period. {@code stated} holds every source, zero included; {@code unanswered} is the total
     * minus the stated sources, and {@code answerRate} is the answered share in percent, null for a period without joins.
     */
    public record Period(LocalDate start, LocalDate end, long total, long unanswered, Double answerRate,
                         Map<StatedSource, Long> stated) {
    }

    /** The new players of a period range, one entry per period in date order. */
    public record NewPlayers(Grouping grouping, LocalDate from, LocalDate to, List<Period> periods) {
    }

    /** The estimated first joins of one post in its attribution window. */
    public record PostEstimate(UUID postId, double estimatedFirstJoins, int windowHours) {
    }

    /** The estimated first joins of one series; {@code seriesId} is null for the posts without a series. */
    public record SeriesEstimate(UUID seriesId, String name, double estimatedFirstJoins) {
    }

    /** The series estimates of a date range. */
    public record SeriesEstimates(LocalDate from, LocalDate to, List<SeriesEstimate> series) {
    }
}
