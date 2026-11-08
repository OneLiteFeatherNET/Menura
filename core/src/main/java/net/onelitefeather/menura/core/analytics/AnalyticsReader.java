package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.ContentSeries;
import net.onelitefeather.menura.core.series.SeriesStore;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The read models of content analytics (change add-content-analytics): the overview across channels, per channel and per
 * format, the series comparison per week, the comparison at equal age by publish hour and weekday, and the analytics of
 * one post. Aggregates are computed from the latest values and the snapshots, never from raw points; raw points are read
 * only through {@link #points}.
 */
public final class AnalyticsReader {

    /** The period of a request that names no dates: the last this many days, the current day included. */
    public static final int DEFAULT_DAYS = 90;

    /** The longest period one request may ask for. */
    public static final int MAX_DAYS = 800;

    /** The most raw points one drill-down returns. */
    public static final int MAX_POINTS = 1000;

    private final Transactions transactions;
    private final Clock clock;
    private final MetricStore metrics;
    private final ExternalPostStore posts;
    private final ChannelStore channels;
    private final SeriesStore series;

    public AnalyticsReader(Transactions transactions, Clock clock, MetricStore metrics, ExternalPostStore posts,
                           ChannelStore channels, SeriesStore series) {
        this.transactions = transactions;
        this.clock = clock;
        this.metrics = metrics;
        this.posts = posts;
        this.channels = channels;
        this.series = series;
    }

    /** A labelled group of an aggregate: a channel, a format or a series. */
    public record Row(String key, String label, Aggregation.Group group) {
    }

    /** The last collection of one channel, with its display name. */
    public record ChannelStatus(UUID channelId, String displayName, String platform, CollectionStatus status) {
    }

    /**
     * The overview of the posts published in the period: totals across channels, per channel, per format, and the last
     * collection of every channel.
     */
    public record Overview(LocalDate from, LocalDate to, Aggregation.Group totals, List<Row> channels, List<Row> formats,
                           List<ChannelStatus> collections) {
    }

    /** One week of one series. */
    public record WeekRow(String isoWeek, LocalDate weekStart, Aggregation.Group group) {
    }

    /** One series of the comparison; {@code seriesId} and {@code label} are null for posts in no series. */
    public record SeriesRow(UUID seriesId, String label, Aggregation.Group group, List<WeekRow> weeks) {
    }

    /** The series comparison, ranked by engaged views. */
    public record SeriesComparison(LocalDate from, LocalDate to, List<SeriesRow> series) {
    }

    /** The grouping of an equal-age comparison: by publish hour of the day, or by ISO weekday. */
    public enum By {
        HOUR(0, 23), WEEKDAY(1, 7);

        private final int first;
        private final int last;

        By(int first, int last) {
            this.first = first;
            this.last = last;
        }

        public int first() {
            return first;
        }

        public int last() {
            return last;
        }

        /** The grouping named by the API: {@code hour} or {@code weekday}. */
        public static Optional<By> parse(String value) {
            if (value == null) {
                return Optional.empty();
            }
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "hour" -> Optional.of(HOUR);
                case "weekday" -> Optional.of(WEEKDAY);
                default -> Optional.empty();
            };
        }
    }

    /** The comparison at equal age: the median of one metric at one offset per slot, with the posts used. */
    public record EqualAge(String metric, Duration offset, By by, UUID channelId, List<Aggregation.Slot> slots) {
    }

    /** The analytics of one post: its latest values, its snapshots and its record. */
    public record PostDetail(ExternalPost post, List<PointValue> latest, List<SnapshotValue> snapshots) {
    }

    /** The overview of the posts published from {@code from} to {@code to}, both included, in the team's time zone. */
    public Overview overview(LocalDate from, LocalDate to, Set<Role> roles) throws SQLException {
        Period period = period(from, to);
        return transactions.inTransaction(c -> {
            List<Observation> observations = metrics.latest(c, period.start(), period.end(), roleNames(roles));
            Map<UUID, String> names = new HashMap<>();
            List<ChannelStatus> statuses = new ArrayList<>();
            for (CollectionStatus status : metrics.statuses(c)) {
                Optional<Channel> channel = channels.findChannel(c, status.channelId());
                if (channel.isPresent()) {
                    names.put(status.channelId(), channel.get().displayName());
                    statuses.add(new ChannelStatus(status.channelId(), channel.get().displayName(),
                            channel.get().platform(), status));
                }
            }
            for (Observation observation : observations) {
                if (!names.containsKey(observation.channelId())) {
                    names.put(observation.channelId(), channels.findChannel(c, observation.channelId())
                            .map(Channel::displayName).orElse(""));
                }
            }
            Aggregation.Group totals = Aggregation.total("all", observations);
            List<Row> byChannel = labelled(Aggregation.groups(observations, o -> o.channelId().toString()), names);
            List<Row> byFormat = labelled(Aggregation.groups(observations, Observation::format), Map.of());
            return new Overview(from, to, totals, byChannel, byFormat, statuses);
        });
    }

    /** The series comparison of the posts published from {@code from} to {@code to}, per series and per ISO week. */
    public SeriesComparison series(LocalDate from, LocalDate to, Set<Role> roles) throws SQLException {
        Period period = period(from, to);
        return transactions.inTransaction(c -> {
            List<Observation> observations = metrics.latest(c, period.start(), period.end(), roleNames(roles));
            Map<UUID, String> names = new HashMap<>();
            for (ContentSeries found : series.list(c)) {
                names.put(found.id(), found.name());
            }
            Map<String, List<Observation>> bySeries = new LinkedHashMap<>();
            for (Observation observation : observations) {
                String key = observation.seriesId() == null ? "" : observation.seriesId().toString();
                bySeries.computeIfAbsent(key, k -> new ArrayList<>()).add(observation);
            }
            List<SeriesRow> rows = new ArrayList<>();
            for (Map.Entry<String, List<Observation>> entry : bySeries.entrySet()) {
                List<Observation> own = entry.getValue();
                UUID seriesId = entry.getKey().isEmpty() ? null : UUID.fromString(entry.getKey());
                String label = seriesId == null ? null : names.getOrDefault(seriesId, "");
                List<WeekRow> weeks = new ArrayList<>();
                Map<String, List<Observation>> byWeek = new TreeMap<>();
                for (Observation observation : own) {
                    byWeek.computeIfAbsent(PublishSlot.isoWeek(observation.publishedAt()), k -> new ArrayList<>())
                            .add(observation);
                }
                for (Map.Entry<String, List<Observation>> week : byWeek.entrySet()) {
                    Instant anyPublish = week.getValue().get(0).publishedAt();
                    weeks.add(new WeekRow(week.getKey(), PublishSlot.weekStart(anyPublish),
                            Aggregation.total(week.getKey(), week.getValue())));
                }
                rows.add(new SeriesRow(seriesId, label, Aggregation.total(entry.getKey(), own), weeks));
            }
            rows.sort(Comparator.comparingDouble((SeriesRow row) -> -engagedViews(row.group()))
                    .thenComparing(row -> row.label() == null ? "" : row.label()));
            return new SeriesComparison(from, to, rows);
        });
    }

    /**
     * The comparison at equal age: for each publish hour or weekday in the team's time zone, the median of the metric over
     * the posts that have a snapshot at the offset. Posts younger than the offset have none and are not counted.
     */
    public EqualAge equalAge(String metric, Duration offset, By by, UUID channelId) throws SQLException {
        if (!Metrics.isKnown(metric)) {
            throw new ProblemException(400, "Invalid metric", "The metric is not known.");
        }
        if (!Offsets.allowed(offset)) {
            throw new ProblemException(400, "Invalid offset",
                    "The offset must be +1, +2, +4, +8, +24, +48 hours, +7 days or a whole day from day 8 to day "
                            + Offsets.MAX_DAY + ".");
        }
        return transactions.inTransaction(c -> {
            List<Observation> observations = metrics.snapshots(c, offset, metric, channelId);
            ToIntFunction<Observation> slot = by == By.HOUR
                    ? o -> PublishSlot.hour(o.publishedAt())
                    : o -> PublishSlot.weekday(o.publishedAt());
            List<Aggregation.Slot> slots = Aggregation.medianBySlot(observations, metric, by.first(), by.last(), slot);
            return new EqualAge(metric, offset, by, channelId, slots);
        });
    }

    /** The analytics of one post; a post that does not exist answers 404. */
    public PostDetail post(UUID postId) throws SQLException {
        return transactions.inTransaction(c -> {
            ExternalPost post = posts.find(c, postId).orElseThrow(() -> notFound());
            return new PostDetail(post, metrics.latestOf(c, postId), metrics.snapshotsOf(c, postId));
        });
    }

    /** The raw points of one post, optionally of one metric, in collection-time order. */
    public List<PointValue> points(UUID postId, String metric, int limit) throws SQLException {
        if (metric != null && !Metrics.isKnown(metric)) {
            throw new ProblemException(400, "Invalid metric", "The metric is not known.");
        }
        int bounded = Math.max(1, Math.min(limit, MAX_POINTS));
        return transactions.inTransaction(c -> {
            if (posts.find(c, postId).isEmpty()) {
                throw notFound();
            }
            return metrics.points(c, postId, metric, bounded);
        });
    }

    /** The period in days, both included, with the default when no date is given. */
    public LocalDate defaultFrom() {
        return LocalDate.now(clock.withZone(PublishSlot.ZONE)).minusDays(DEFAULT_DAYS - 1L);
    }

    /** Today in the team's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(PublishSlot.ZONE));
    }

    static double engagedViews(Aggregation.Group group) {
        for (Aggregation.Total total : group.metrics()) {
            if (Metrics.ENGAGED_VIEWS.equals(total.name()) && total.value() != null) {
                return total.value();
            }
        }
        return 0;
    }

    private static Period period(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ProblemException(400, "Invalid period", "The period must start on or before its end date.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ProblemException(400, "Invalid period", "The period may cover at most " + MAX_DAYS + " days.");
        }
        return new Period(PublishSlot.startOf(from), PublishSlot.startOf(to.plusDays(1)));
    }

    private static Set<String> roleNames(Set<Role> roles) {
        Set<String> names = new HashSet<>();
        for (Role role : roles) {
            names.add(role.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static List<Row> labelled(List<Aggregation.Group> groups, Map<UUID, String> names) {
        List<Row> rows = new ArrayList<>();
        for (Aggregation.Group group : groups) {
            UUID id = parseOrNull(group.key());
            String label = id == null ? group.key() : names.getOrDefault(id, "");
            rows.add(new Row(group.key(), label, group));
        }
        return rows;
    }

    private static UUID parseOrNull(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static ProblemException notFound() {
        return new ProblemException(404, "Not found", "No external post with this identifier is visible to you.");
    }

    private record Period(Instant start, Instant end) {
    }
}
