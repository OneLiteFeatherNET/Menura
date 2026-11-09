package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.InternalPoint;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The registered internal metric sources (change add-content-analytics): a source is registered with the metric names it
 * may write, and a point of any other source or metric is refused before anything is stored. Points of a source are stored,
 * rolled up and read like platform metrics, marked with their source and estimate flag. A source can never write a platform
 * metric name, so it cannot change a platform point.
 */
public final class InternalMetrics {

    private static final Pattern NAME = Pattern.compile("^[a-z_]{1,40}$");

    /** A registered source: its name and the metric names it may write. */
    public record Source(String name, Set<String> metrics) {

        public Source {
            if (!NAME.matcher(name).matches() || Rollups.PLATFORM.equals(name)) {
                throw new IllegalArgumentException("the source name is not allowed: " + name);
            }
            metrics = Set.copyOf(metrics);
            for (String metric : metrics) {
                if (!NAME.matcher(metric).matches() || Metrics.isKnown(metric)) {
                    throw new IllegalArgumentException("the metric name is not allowed for a source: " + metric);
                }
            }
            if (metrics.isEmpty()) {
                throw new IllegalArgumentException("a source registers at least one metric");
            }
        }
    }

    /** A point the registration or the scope does not allow; nothing is stored. */
    public static final class Refused extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public Refused(String message) {
            super(message);
        }
    }

    private final Map<String, Source> sources = new TreeMap<>();
    private final MetricStore metrics;
    private final Clock clock;

    public InternalMetrics(MetricStore metrics, Clock clock, Collection<Source> registered) {
        this.metrics = metrics;
        this.clock = clock;
        for (Source source : registered) {
            if (sources.put(source.name(), source) != null) {
                throw new IllegalArgumentException("the source is registered twice: " + source.name());
            }
        }
    }

    /** The registration of a source by its name. */
    public Optional<Source> find(String name) {
        return Optional.ofNullable(sources.get(name));
    }

    /**
     * Stores one point of a registered source. The scope is the installation (no ids), a channel, an external post (its
     * channel too) or a series; a point with more than one scope is refused.
     */
    public void write(Connection connection, InternalPoint point) throws SQLException {
        Source source = find(point.source()).orElseThrow(() -> new Refused("the source is not registered"));
        if (!source.metrics().contains(point.metric())) {
            throw new Refused("the source does not write this metric");
        }
        if (point.value() < 0) {
            throw new Refused("a value is never negative");
        }
        if (point.day() == null) {
            throw new Refused("an internal value names the day it refers to");
        }
        if (point.postId() != null && (point.channelId() == null || point.seriesId() != null)) {
            throw new Refused("a post scope names its channel and no series");
        }
        metrics.storeInternal(connection, point, clock.instant());
    }

    /** The source names registered, sorted. */
    public Set<String> names() {
        return Set.copyOf(sources.keySet());
    }
}
