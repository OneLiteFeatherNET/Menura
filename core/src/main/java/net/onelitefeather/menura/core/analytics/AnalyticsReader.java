package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.analytics.Rollups.OffsetGroup;
import net.onelitefeather.menura.core.analytics.Rollups.Rollup;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.ContentSeries;
import net.onelitefeather.menura.core.series.SeriesAssignment;
import net.onelitefeather.menura.core.series.SeriesStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
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
import java.util.TreeSet;
import java.util.UUID;

/**
 * The read models of content analytics (change add-content-analytics): the overview across channels, per channel and per
 * format, the series comparison per week, the comparison at equal age by publish hour and weekday, and the analytics of
 * one post. Every aggregate is read from the rollups (daily, weekly and the equal-age groups), never from the raw points or
 * the latest values; raw points are read only through {@link #points}.
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
    private final PublishSlot slots;
    private final MetricStore metrics;
    private final ExternalPostStore posts;
    private final ChannelStore channels;
    private final SeriesStore series;

    public AnalyticsReader(Transactions transactions, Clock clock, PublishSlot slots, MetricStore metrics,
                           ExternalPostStore posts, ChannelStore channels, SeriesStore series) {
        this.transactions = transactions;
        this.clock = clock;
        this.slots = slots;
        this.metrics = metrics;
        this.posts = posts;
        this.channels = channels;
        this.series = series;
    }

    /** A labelled group of an aggregate: a channel or a format. */
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

    /**
     * One metric of an internal source over a period: the sum of its values and their count, with the estimate flag. A value
     * is null when no value of the metric was stored.
     */
    public record InternalTotal(String source, String metric, boolean estimate, Double value, int values) {
    }

    /** One week of one series: the platform aggregate and the internal metrics of the series that week. */
    public record WeekRow(String isoWeek, LocalDate weekStart, Aggregation.Group group, List<InternalTotal> internal) {
    }

    /**
     * One series of the comparison; {@code seriesId} and {@code label} are null for posts in no series. The internal metrics
     * are the series' values of the registered sources over the period.
     */
    public record SeriesRow(UUID seriesId, String label, Aggregation.Group group, List<WeekRow> weeks,
                            List<InternalTotal> internal) {
    }

    /** The series comparison over the ISO weeks overlapping the period, ranked by engaged views. */
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
    public record EqualAge(String metric, Duration offset, By by, UUID channelId, String timeZone,
                           List<Aggregation.Slot> slots) {
    }

    /** The analytics of one post: its latest values, its snapshots and its record. */
    public record PostDetail(ExternalPost post, List<PointValue> latest, List<SnapshotValue> snapshots) {
    }

    /** The overview of the posts published from {@code from} to {@code to}, both included, in the team's time zone. */
    public Overview overview(LocalDate from, LocalDate to, Set<Role> roles) throws SQLException {
        validate(from, to);
        return transactions.inTransaction(c -> {
            List<Rollup> rows = platformRows(metrics.dailyRows(c, from, to, roleNames(roles)));
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
            for (Rollup row : rows) {
                if (row.grain().channelId() != null && !names.containsKey(row.grain().channelId())) {
                    names.put(row.grain().channelId(), channels.findChannel(c, row.grain().channelId())
                            .map(Channel::displayName).orElse(""));
                }
            }
            Aggregation.Group totals = Aggregation.total("all", rows);
            List<Row> byChannel = labelled(Aggregation.groups(rows, r -> String.valueOf(r.grain().channelId())), names);
            List<Row> byFormat = labelled(Aggregation.groups(rows, r -> String.valueOf(r.grain().format())), Map.of());
            return new Overview(from, to, totals, byChannel, byFormat, statuses);
        });
    }

    /**
     * The series comparison over the ISO weeks that overlap the period, per series and per week. Internal metrics are shown
     * for the series they were written for, with their estimate flag.
     */
    public SeriesComparison series(LocalDate from, LocalDate to, Set<Role> roles) throws SQLException {
        validate(from, to);
        LocalDate firstWeek = MetricRollups.weekStart(from);
        return transactions.inTransaction(c -> {
            List<Rollup> rows = metrics.weeklyRows(c, firstWeek, to, roleNames(roles));
            Map<UUID, String> names = new HashMap<>();
            for (ContentSeries found : series.list(c)) {
                names.put(found.id(), found.name());
            }
            Map<String, List<Rollup>> bySeries = new LinkedHashMap<>();
            for (Rollup row : rows) {
                bySeries.computeIfAbsent(keyOf(row.grain().seriesId()), k -> new ArrayList<>()).add(row);
            }
            List<SeriesRow> result = new ArrayList<>();
            for (Map.Entry<String, List<Rollup>> entry : bySeries.entrySet()) {
                UUID seriesId = entry.getKey().isEmpty() ? null : UUID.fromString(entry.getKey());
                List<Rollup> own = entry.getValue();
                // Internal values count for a series only; a channel or installation value is not a series value.
                List<Rollup> platform = platformRows(own);
                List<Rollup> internal = seriesId == null ? List.of() : own.stream()
                        .filter(row -> !row.grain().platformValue()).toList();
                Map<LocalDate, List<Rollup>> byWeek = new TreeMap<>();
                for (Rollup row : platform) {
                    byWeek.computeIfAbsent(row.period(), k -> new ArrayList<>()).add(row);
                }
                Map<LocalDate, List<Rollup>> internalByWeek = new TreeMap<>();
                for (Rollup row : internal) {
                    internalByWeek.computeIfAbsent(row.period(), k -> new ArrayList<>()).add(row);
                }
                TreeSet<LocalDate> weekStarts = new TreeSet<>(byWeek.keySet());
                weekStarts.addAll(internalByWeek.keySet());
                List<WeekRow> weeks = new ArrayList<>();
                for (LocalDate weekStart : weekStarts) {
                    List<Rollup> weekPlatform = byWeek.getOrDefault(weekStart, List.of());
                    List<InternalTotal> weekInternal = internalTotals(internalByWeek.getOrDefault(weekStart, List.of()));
                    weeks.add(new WeekRow(isoWeek(weekStart), weekStart, Aggregation.total(isoWeek(weekStart), weekPlatform),
                            weekInternal));
                }
                result.add(new SeriesRow(seriesId, seriesId == null ? null : names.getOrDefault(seriesId, ""),
                        Aggregation.total(entry.getKey(), platform), weeks, internalTotals(internal)));
            }
            result.sort(Comparator.comparingDouble((SeriesRow row) -> -engagedViews(row.group()))
                    .thenComparing(row -> row.label() == null ? "" : row.label()));
            return new SeriesComparison(from, to, result);
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
            List<OffsetGroup> groups = metrics.offsetGroups(c, offset, metric, channelId);
            List<Aggregation.Slot> buckets = by == By.HOUR
                    ? Aggregation.medianBySlot(groups, metric, by.first(), by.last(), OffsetGroup::publishHour)
                    : Aggregation.medianBySlot(groups, metric, by.first(), by.last(), OffsetGroup::publishWeekday);
            return new EqualAge(metric, offset, by, channelId, slots.zone().getId(), buckets);
        });
    }

    /** The analytics of one post a caller may see; a post that does not exist, or whose series the roles may not see, is 404. */
    public PostDetail post(UUID postId, Set<Role> roles) throws SQLException {
        return transactions.inTransaction(c -> {
            ExternalPost post = visible(c, postId, roles);
            return new PostDetail(post, metrics.latestOf(c, postId), metrics.snapshotsOf(c, postId));
        });
    }

    /** The raw points of one post a caller may see, optionally of one metric, in collection-time order. */
    public List<PointValue> points(UUID postId, Set<Role> roles, String metric, int limit) throws SQLException {
        if (metric != null && !Metrics.isKnown(metric)) {
            throw new ProblemException(400, "Invalid metric", "The metric is not known.");
        }
        int bounded = Math.max(1, Math.min(limit, MAX_POINTS));
        return transactions.inTransaction(c -> {
            visible(c, postId, roles);
            return metrics.points(c, postId, metric, bounded);
        });
    }

    /** The period in days, both included, with the default when no date is given. */
    public LocalDate defaultFrom() {
        return LocalDate.now(clock.withZone(slots.zone())).minusDays(DEFAULT_DAYS - 1L);
    }

    /** Today in the team's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(slots.zone()));
    }

    static double engagedViews(Aggregation.Group group) {
        for (Aggregation.Total total : group.metrics()) {
            if (Metrics.ENGAGED_VIEWS.equals(total.name()) && total.value() != null) {
                return total.value();
            }
        }
        return 0;
    }

    /** The post, when its series is visible to the roles; otherwise the same 404 as a post that does not exist. */
    private ExternalPost visible(Connection c, UUID postId, Set<Role> roles) throws SQLException {
        ExternalPost post = posts.find(c, postId).orElseThrow(() -> notFound());
        Optional<SeriesAssignment> assignment = series.findAssignment(c, postId);
        if (assignment.isPresent() && assignment.get().seriesId() != null) {
            Optional<ContentSeries> found = series.find(c, assignment.get().seriesId());
            if (found.isPresent() && !found.get().isVisibleTo(roles)) {
                throw notFound();
            }
        }
        return post;
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ProblemException(400, "Invalid period", "The period must start on or before its end date.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ProblemException(400, "Invalid period", "The period may cover at most " + MAX_DAYS + " days.");
        }
    }

    private static List<Rollup> platformRows(List<Rollup> rows) {
        return rows.stream().filter(row -> row.grain().platformValue()).toList();
    }

    /** The internal metrics of rows, summed per source, metric and estimate flag, sorted. */
    static List<InternalTotal> internalTotals(List<Rollup> rows) {
        Map<String, Rollup> first = new TreeMap<>();
        Map<String, long[]> sums = new TreeMap<>();
        for (Rollup row : rows) {
            if (row.grain().platformValue()) {
                continue;
            }
            String key = row.grain().source() + "\u0000" + row.metric() + "\u0000" + row.grain().estimate();
            first.putIfAbsent(key, row);
            long[] sum = sums.computeIfAbsent(key, k -> new long[2]);
            sum[0] += row.valuesCount();
            sum[1] += row.total();
        }
        List<InternalTotal> totals = new ArrayList<>();
        for (Map.Entry<String, Rollup> entry : first.entrySet()) {
            long[] sum = sums.get(entry.getKey());
            Rollup row = entry.getValue();
            totals.add(new InternalTotal(row.grain().source(), row.metric(), row.grain().estimate(),
                    sum[0] == 0 ? null : (double) sum[1], (int) sum[0]));
        }
        return totals;
    }

    private static String keyOf(UUID seriesId) {
        return seriesId == null ? "" : seriesId.toString();
    }

    private static String isoWeek(LocalDate weekStart) {
        return String.format("%04d-W%02d", weekStart.get(IsoFields.WEEK_BASED_YEAR),
                weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
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
}
