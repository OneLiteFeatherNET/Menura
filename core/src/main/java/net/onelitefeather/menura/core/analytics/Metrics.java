package net.onelitefeather.menura.core.analytics;

import java.util.List;
import java.util.Set;

/**
 * The metric names of external posts (change add-content-analytics). Every name is a lower-case snake_case key that the
 * platform adapters report through their metrics operation; a name that is not listed here is never stored.
 *
 * <p>Counts are cumulative totals of the post. {@link #AVERAGE_VIEW_PERCENTAGE} is a percentage and is aggregated as an
 * average over posts, not as a sum.
 */
public final class Metrics {

    public static final String VIEWS = "views";
    public static final String ENGAGED_VIEWS = "engaged_views";
    public static final String AVERAGE_VIEW_PERCENTAGE = "average_view_percentage";
    /** Watch time in seconds. */
    public static final String WATCH_TIME = "watch_time";
    public static final String LIKES = "likes";
    public static final String COMMENTS = "comments";
    public static final String SHARES = "shares";

    /** Every metric the collection requests, in the order the dashboard shows them. */
    public static final List<String> ALL = List.of(VIEWS, ENGAGED_VIEWS, AVERAGE_VIEW_PERCENTAGE, WATCH_TIME, LIKES,
            COMMENTS, SHARES);

    private Metrics() {
    }

    public static Set<String> requested() {
        return Set.copyOf(ALL);
    }

    /** True for the metrics a group reports as an average over its posts instead of a sum. */
    public static boolean isAverage(String metric) {
        return AVERAGE_VIEW_PERCENTAGE.equals(metric);
    }

    public static boolean isKnown(String metric) {
        return ALL.contains(metric);
    }
}
