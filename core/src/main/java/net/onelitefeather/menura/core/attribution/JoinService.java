package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.attribution.JoinReports.Grouping;
import net.onelitefeather.menura.core.attribution.JoinReports.NewPlayers;
import net.onelitefeather.menura.core.attribution.JoinReports.Period;
import net.onelitefeather.menura.core.attribution.JoinReports.PostEstimate;
import net.onelitefeather.menura.core.attribution.JoinReports.SeriesEstimate;
import net.onelitefeather.menura.core.attribution.JoinReports.SeriesEstimates;
import net.onelitefeather.menura.core.attribution.JoinStore.DayCount;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Join attribution (change add-join-attribution): accepts the join events and the source answers of the network proxy,
 * keeps the daily join rollup in the same transaction as each accepted event or answer, and reads the new-player reports
 * and the estimates per post and series. Every write is idempotent by the proxy's event id.
 */
public final class JoinService {

    /** How long after the join a source answer is still accepted. */
    public static final int ANSWER_WINDOW_HOURS = 24;

    /** The longest period a report may cover, in days. */
    public static final int MAX_PERIOD_DAYS = 366;

    /** The rollup counter of every first join of a day. */
    static final String TOTAL = "total";

    private final Transactions transactions;
    private final Clock clock;
    private final ZoneId zone;
    private final JoinStore store;

    public JoinService(Transactions transactions, Clock clock, ZoneId zone, JoinStore store) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** The outcome of an accepted write: the stored value, and whether this call created it. */
    public record Recorded<T>(T value, boolean created) {
    }

    /**
     * Accepts a join event. A new event id is stored and, for a first join, counted once. A repeated event with the same
     * content changes nothing; the same id with other content is a 409 and the stored event stays.
     */
    public Recorded<JoinEvent> recordJoin(JoinPayloads.EventInput input) throws SQLException {
        return transactions.inTransaction(connection -> {
            JoinEvent event = new JoinEvent(input.eventId(), input.firstJoin(), input.joinedAt(), input.pseudonym(),
                    clock.instant());
            Optional<JoinEvent> stored = store.findEvent(connection, event.eventId());
            if (stored.isEmpty() && store.insertEvent(connection, event)) {
                if (event.firstJoin()) {
                    store.addToDay(connection, dayOf(event.joinedAt()), TOTAL, 1);
                }
                return new Recorded<>(event, true);
            }
            JoinEvent existing = stored.isPresent() ? stored.get() : store.findEvent(connection, event.eventId()).orElseThrow();
            if (!existing.sameContent(event)) {
                throw new ProblemException(409, "Conflict", "The event id is stored with other content.");
            }
            return new Recorded<>(existing, false);
        });
    }

    /**
     * Accepts the source of a first join, at most once and within 24 hours of the join. A repeated answer with the same
     * source changes nothing; a different one is a 409 and the first one stands.
     */
    public Recorded<JoinAnswer> recordAnswer(UUID eventId, StatedSource source) throws SQLException {
        return transactions.inTransaction(connection -> {
            JoinEvent join = store.findEvent(connection, eventId).orElseThrow(() -> new ProblemException(404,
                    "Not Found", "No join with this event id."));
            if (!join.firstJoin()) {
                throw new ProblemException(422, "Not a first join", "Only a first join takes a source answer.");
            }
            Instant now = clock.instant();
            if (now.isAfter(join.joinedAt().plus(Duration.ofHours(ANSWER_WINDOW_HOURS)))) {
                throw new ProblemException(422, "Answer too late", "The source of this join can no longer be answered.");
            }
            JoinAnswer answer = new JoinAnswer(eventId, source, now);
            Optional<JoinAnswer> stored = store.findAnswer(connection, eventId);
            if (stored.isEmpty() && store.insertAnswer(connection, answer)) {
                store.addToDay(connection, dayOf(join.joinedAt()), source.wire(), 1);
                return new Recorded<>(answer, true);
            }
            JoinAnswer existing = stored.isPresent() ? stored.get() : store.findAnswer(connection, eventId).orElseThrow();
            if (existing.source() != source) {
                throw new ProblemException(409, "Conflict", "The join already has a different source.");
            }
            return new Recorded<>(existing, false);
        });
    }

    /** Today in the installation's time zone; the default end of a report period. */
    public LocalDate today() {
        return clock.instant().atZone(zone).toLocalDate();
    }

    /** The installation's join settings; the defaults until an admin saved them. */
    public JoinSettings settings() throws SQLException {
        return transactions.inTransaction(connection -> store.findSettings(connection).orElse(JoinSettings.unset()));
    }

    /**
     * Saves the join domain and, when given, the attribution window. Both are checked before anything is written. The
     * window keeps its value when none is sent.
     */
    public JoinSettings saveSettings(UUID actor, String domain, Integer windowHours) throws SQLException {
        String checkedDomain = JoinSettings.checkedDomain(domain);
        Integer checkedWindow = windowHours == null ? null : JoinSettings.checkedWindowHours(windowHours);
        return transactions.inTransaction(connection -> {
            int window = checkedWindow != null ? checkedWindow
                    : store.findSettings(connection).orElse(JoinSettings.unset()).attributionWindowHours();
            JoinSettings saved = new JoinSettings(checkedDomain, window, clock.instant(), actor);
            store.saveSettings(connection, saved);
            return saved;
        });
    }

    /**
     * The new players per period: totals, the stated sources, the unanswered joins and the answer rate. Periods are days or
     * ISO weeks, clipped to the requested range.
     */
    public NewPlayers newPlayers(LocalDate from, LocalDate to, Grouping grouping) throws SQLException {
        checkPeriod(from, to);
        return transactions.inTransaction(connection -> {
            List<DayCount> counts = store.countsBetween(connection, from, to);
            List<Period> periods = new ArrayList<>();
            for (LocalDate[] range : ranges(from, to, grouping)) {
                periods.add(period(range[0], range[1], counts));
            }
            return new NewPlayers(grouping, from, to, periods);
        });
    }

    /**
     * The estimated first joins of one post. A post the caller may not see, because its series is hidden from the caller's
     * roles, is not found.
     */
    public PostEstimate postEstimate(UUID postId, Set<Role> roles) throws SQLException {
        return transactions.inTransaction(connection -> {
            AttributablePost post = store.findPost(connection, postId).filter(found -> found.visibleTo(roles))
                    .orElseThrow(() -> new ProblemException(404, "Not Found", "No external post with this id."));
            JoinSettings settings = store.findSettings(connection).orElse(JoinSettings.unset());
            Duration window = settings.window();
            Instant published = post.publishedAt();
            List<FirstJoin> joins = store.firstJoinsBetween(connection, published, published.plus(window));
            List<AttributablePost> posts = store.postsPublishedBetween(connection, published.minus(window),
                    published.plus(window));
            double estimate = JoinAttribution.perPost(joins, posts, window).getOrDefault(postId, 0.0);
            return new PostEstimate(postId, estimate, settings.attributionWindowHours());
        });
    }

    /**
     * The estimated first joins per series for the joins of a date range. Posts of series the roles may not see are left
     * out; posts without a series are summed under "no series", which comes last.
     */
    public SeriesEstimates seriesEstimates(LocalDate from, LocalDate to, Set<Role> roles) throws SQLException {
        checkPeriod(from, to);
        return transactions.inTransaction(connection -> {
            Duration window = store.findSettings(connection).orElse(JoinSettings.unset()).window();
            Instant start = from.atStartOfDay(zone).toInstant();
            Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
            List<FirstJoin> joins = inRange(store.firstJoinsBetween(connection, start, end), start, end);
            List<AttributablePost> posts = store.postsPublishedBetween(connection, start.minus(window), end);
            Map<UUID, Double> perPost = JoinAttribution.perPost(joins, posts, window);
            Map<UUID, Double> perSeries = new LinkedHashMap<>();
            Map<UUID, String> names = new LinkedHashMap<>();
            double noSeries = 0;
            boolean anyNoSeries = false;
            for (AttributablePost post : posts) {
                if (!post.visibleTo(roles)) {
                    continue;
                }
                double estimate = perPost.getOrDefault(post.postId(), 0.0);
                if (post.seriesId() == null) {
                    noSeries += estimate;
                    anyNoSeries = true;
                } else {
                    perSeries.merge(post.seriesId(), estimate, Double::sum);
                    names.put(post.seriesId(), post.seriesName());
                }
            }
            List<SeriesEstimate> series = new ArrayList<>();
            for (Map.Entry<UUID, Double> entry : perSeries.entrySet()) {
                series.add(new SeriesEstimate(entry.getKey(), names.get(entry.getKey()), entry.getValue()));
            }
            series.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            if (anyNoSeries) {
                series.add(new SeriesEstimate(null, JoinReports.NO_SERIES, noSeries));
            }
            return new SeriesEstimates(from, to, series);
        });
    }

    /**
     * Rebuilds the rollup of one day from the stored events and answers, replacing its counters. This is the correction
     * path: the counters always equal the stored first joins of the day afterwards.
     */
    public void rebuildDay(LocalDate day) throws SQLException {
        transactions.inTransaction(connection -> {
            Instant start = day.atStartOfDay(zone).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
            List<FirstJoin> joins = inRange(store.firstJoinsBetween(connection, start, end), start, end);
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put(TOTAL, (long) joins.size());
            for (StatedSource source : StatedSource.values()) {
                long answered = joins.stream().filter(join -> join.source() == source).count();
                if (answered > 0) {
                    counts.put(source.wire(), answered);
                }
            }
            store.replaceDay(connection, day, counts);
            return null;
        });
    }

    private Period period(LocalDate start, LocalDate end, List<DayCount> counts) {
        long total = 0;
        Map<StatedSource, Long> stated = new EnumMap<>(StatedSource.class);
        for (StatedSource source : StatedSource.values()) {
            stated.put(source, 0L);
        }
        for (DayCount count : counts) {
            if (count.day().isBefore(start) || count.day().isAfter(end)) {
                continue;
            }
            if (TOTAL.equals(count.source())) {
                total += count.joins();
            } else {
                StatedSource.parse(count.source()).ifPresent(source -> stated.merge(source, count.joins(), Long::sum));
            }
        }
        long answered = stated.values().stream().mapToLong(Long::longValue).sum();
        Double rate = total == 0 ? null : answered * 100.0 / total;
        return new Period(start, end, total, total - answered, rate, stated);
    }

    /** The periods of a range: each day, or each ISO week clipped to the range. */
    private static List<LocalDate[]> ranges(LocalDate from, LocalDate to, Grouping grouping) {
        List<LocalDate[]> ranges = new ArrayList<>();
        if (grouping == Grouping.DAY) {
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                ranges.add(new LocalDate[] {day, day});
            }
            return ranges;
        }
        for (LocalDate monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
             !monday.isAfter(to); monday = monday.plusWeeks(1)) {
            LocalDate start = monday.isBefore(from) ? from : monday;
            LocalDate end = monday.plusDays(6).isAfter(to) ? to : monday.plusDays(6);
            ranges.add(new LocalDate[] {start, end});
        }
        return ranges;
    }

    private static List<FirstJoin> inRange(List<FirstJoin> joins, Instant start, Instant end) {
        return joins.stream().filter(join -> !join.joinedAt().isBefore(start) && join.joinedAt().isBefore(end)).toList();
    }

    private LocalDate dayOf(Instant joinedAt) {
        return joinedAt.atZone(zone).toLocalDate();
    }

    private static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw new ProblemException(400, "Invalid period",
                    "The period must start on or before its end and cover at most " + MAX_PERIOD_DAYS + " days.");
        }
    }
}
