package net.onelitefeather.menura.server.prototype.analytics;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.prototype.Model;
import net.onelitefeather.menura.server.prototype.analytics.MetricCatalogue.Metric;
import net.onelitefeather.menura.server.prototype.analytics.Snapshot.Offset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns the snapshots of the posts into the answers of the analytics API. Calendar groupings use the publish time in
 * Europe/Berlin. Count metrics are summed per group, averages are averaged, and publish hour and weekday compare the
 * posts at the same offset by their median, so early and late posts are compared fairly.
 */
public final class Aggregation {

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String OFFSETS = "+1h, +2h, +4h, +8h, +24h, +48h, +7d";

    public enum GroupBy {
        DAY("day"), WEEK("week"), CHANNEL("channel"), SERIES("series"), PUBLISH_HOUR("publishHour"),
        WEEKDAY("weekday");

        private final String wire;

        GroupBy(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        /** Publish hour and weekday compare medians of the posts at the same offset, not sums. */
        boolean atPublishTime() {
            return this == PUBLISH_HOUR || this == WEEKDAY;
        }

        public static GroupBy parse(String wire) {
            for (GroupBy groupBy : values()) {
                if (groupBy.wire.equals(wire)) {
                    return groupBy;
                }
            }
            throw unprocessable("groupBy: erlaubt sind day, week, channel, series, publishHour, weekday.");
        }
    }

    /** A validated request. {@code channels} are the selected channels in catalogue order. */
    record Selection(Metric metric, List<Model.Channel> channels, Offset offset, Instant from, Instant to) {
    }

    /** One post whose snapshot at the offset carries the metric. */
    record Contribution(Model.Post post, double value) {
    }

    @Serdeable
    public record Row(String key, double value, int posts) {
    }

    @Serdeable
    public record Result(String metric, String groupBy, String offset, List<Row> rows, List<String> missing) {
    }

    @Serdeable
    public record Cell(String weekday, int hour, double value, int posts) {
    }

    @Serdeable
    public record Heatmap(List<Cell> cells, List<String> missing) {
    }

    private Aggregation() {
    }

    /**
     * Validates the query. Every refusal is a 422 whose detail names the field and the allowed values; the raw input is
     * never repeated. An absent offset is +24 h, an absent channel list is every channel.
     */
    static Selection select(List<Model.Channel> all, String metricId, String channelIds, String offsetLabel,
                            String from, String to) {
        if (metricId == null || metricId.isBlank()) {
            throw unprocessable("metric: ist erforderlich.");
        }
        Metric metric = MetricCatalogue.find(metricId)
                .orElseThrow(() -> unprocessable("metric: unbekannte Kennzahl."));
        Offset offset = offsetLabel == null || offsetLabel.isBlank() ? Offset.H24
                : Offset.ofLabel(offsetLabel).orElseThrow(() -> unprocessable("offset: erlaubt sind " + OFFSETS + "."));
        Instant start = bound(from, "from");
        Instant end = bound(to, "to");
        if (start != null && end != null && !start.isBefore(end)) {
            throw unprocessable("from: liegt nicht vor to.");
        }
        return new Selection(metric, channels(all, channelIds), offset, start, end);
    }

    static List<Model.Channel> channels(List<Model.Channel> all, String csv) {
        if (csv == null || csv.isBlank()) {
            return all;
        }
        Set<String> wanted = Arrays.stream(csv.split(","))
                .map(String::trim).filter(id -> !id.isEmpty()).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return all;
        }
        for (String id : wanted) {
            if (all.stream().noneMatch(channel -> channel.id().equals(id))) {
                throw unprocessable("channelIds: unbekannter Kanal.");
            }
        }
        return all.stream().filter(channel -> wanted.contains(channel.id())).toList();
    }

    /** A date (interpreted as the start of that day in Berlin) or an ISO-8601 instant. */
    static Instant bound(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException notAnInstant) {
            try {
                return LocalDate.parse(value).atStartOfDay(BERLIN).toInstant();
            } catch (DateTimeParseException notADate) {
                throw unprocessable(field + ": erlaubt ist ein Datum wie 2026-11-01 oder ein Zeitpunkt in ISO-8601.");
            }
        }
    }

    /** The selected channels whose platform does not deliver the metric. They are never shown as zero. */
    static List<String> missing(Selection selection) {
        return selection.channels().stream()
                .filter(channel -> !MetricCatalogue.provides(selection.metric(), channel.platform()))
                .map(Model.Channel::id)
                .toList();
    }

    /** The snapshots whose time has come by the clock. An offset that lies in the future is not served. */
    static List<Snapshot> available(List<Snapshot> series, Instant now) {
        return series.stream().filter(snapshot -> !snapshot.at().isAfter(now)).toList();
    }

    /**
     * The value each post contributes: its snapshot at the selected offset, when that offset has come by {@code now}.
     * A post younger than the offset is left out; a post whose platform lacks the metric is left out.
     */
    static List<Contribution> contributions(Selection selection, List<Model.Post> posts, Instant now) {
        Set<String> selected = selection.channels().stream().map(Model.Channel::id).collect(Collectors.toSet());
        List<Contribution> contributions = new ArrayList<>();
        for (Model.Post post : posts) {
            if (!selected.contains(post.channelId()) || !MetricCatalogue.provides(selection.metric(), post.platform())) {
                continue;
            }
            Instant published = Instant.parse(post.publishedAt());
            if (selection.from() != null && published.isBefore(selection.from())
                    || selection.to() != null && !published.isBefore(selection.to())) {
                continue;
            }
            snapshotAt(post, selection.offset(), now)
                    .flatMap(snapshot -> snapshot.value(selection.metric().id()))
                    .ifPresent(value -> contributions.add(new Contribution(post, value)));
        }
        return contributions;
    }

    static Optional<Snapshot> snapshotAt(Model.Post post, Offset offset, Instant now) {
        return available(SnapshotSeed.of(post), now).stream()
                .filter(snapshot -> snapshot.offset() == offset)
                .findFirst();
    }

    static List<Row> rows(Selection selection, GroupBy groupBy, List<Contribution> contributions,
                          List<String> seriesOrder) {
        Map<String, List<Double>> groups = new HashMap<>();
        for (Contribution contribution : contributions) {
            String key = keyOf(groupBy, contribution.post());
            if (key != null) {
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(contribution.value());
            }
        }
        return groups.keySet().stream()
                .sorted(order(groupBy, selection, seriesOrder))
                .map(key -> {
                    List<Double> values = groups.get(key);
                    return new Row(key, round(reduce(selection.metric(), groupBy, values)), values.size());
                })
                .toList();
    }

    /** One cell per weekday and hour (Berlin time) that has posts, with the median of those posts. */
    static Heatmap heatmap(Selection selection, List<Contribution> contributions) {
        Map<Integer, List<Double>> cells = new TreeMap<>();
        for (Contribution contribution : contributions) {
            ZonedDateTime local = localTime(contribution.post());
            cells.computeIfAbsent(local.getDayOfWeek().getValue() * 24 + local.getHour(), ignored -> new ArrayList<>())
                    .add(contribution.value());
        }
        List<Cell> list = cells.entrySet().stream()
                .map(entry -> new Cell(DayOfWeek.of(entry.getKey() / 24).name(), entry.getKey() % 24,
                        round(median(entry.getValue())), entry.getValue().size()))
                .toList();
        return new Heatmap(list, missing(selection));
    }

    static String keyOf(GroupBy groupBy, Model.Post post) {
        ZonedDateTime local = localTime(post);
        return switch (groupBy) {
            case DAY -> local.toLocalDate().toString();
            case WEEK -> weekOf(local.toLocalDate());
            case CHANNEL -> post.channelId();
            case SERIES -> post.seriesId();
            case PUBLISH_HOUR -> String.valueOf(local.getHour());
            case WEEKDAY -> local.getDayOfWeek().name();
        };
    }

    static String weekOf(LocalDate day) {
        return String.format(Locale.ROOT, "%04d-W%02d", day.get(IsoFields.WEEK_BASED_YEAR),
                day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    static double reduce(Metric metric, GroupBy groupBy, List<Double> values) {
        if (groupBy.atPublishTime()) {
            return median(values);
        }
        if (MetricCatalogue.isAverage(metric)) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        }
        return values.stream().mapToDouble(Double::doubleValue).sum();
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static Comparator<String> order(GroupBy groupBy, Selection selection, List<String> seriesOrder) {
        List<String> channelOrder = selection.channels().stream().map(Model.Channel::id).toList();
        return switch (groupBy) {
            case DAY, WEEK -> Comparator.<String>naturalOrder();
            case CHANNEL -> Comparator.comparingInt((String key) -> channelOrder.indexOf(key));
            case SERIES -> Comparator.comparingInt((String key) -> seriesOrder.indexOf(key));
            case PUBLISH_HOUR -> Comparator.comparingInt((String key) -> Integer.parseInt(key));
            case WEEKDAY -> Comparator.comparingInt((String key) -> DayOfWeek.valueOf(key).getValue());
        };
    }

    private static ZonedDateTime localTime(Model.Post post) {
        return Instant.parse(post.publishedAt()).atZone(BERLIN);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static ProblemException unprocessable(String detail) {
        return new ProblemException(422, "Unprocessable Content", detail);
    }
}
