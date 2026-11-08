package net.onelitefeather.menura.server.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.analytics.Aggregation;
import net.onelitefeather.menura.core.analytics.AnalyticsReader;
import net.onelitefeather.menura.core.analytics.PublishSlot;
import net.onelitefeather.menura.core.analytics.MetricStore.PointValue;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.post.ExternalPost;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The representations of content analytics over REST (change add-content-analytics). Nullable fields are always sent. */
public final class AnalyticsViews {

    private AnalyticsViews() {
    }

    /** One metric of an aggregate: null when no post provides it, and the number of posts that contributed. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TotalView(String name, Double value, int posts, boolean average) {
    }

    /** One group of an aggregate with every metric in the fixed order. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AggregateView(String key, String label, int posts, List<TotalView> metrics) {
    }

    /** The last collection of one channel. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CollectionView(UUID channelId, String displayName, String platform, String outcome,
                                 Instant lastSuccessAt, Instant lastFailureAt, String failureReason) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OverviewView(LocalDate from, LocalDate to, AggregateView totals, List<AggregateView> channels,
                               List<AggregateView> formats, List<CollectionView> collections) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record WeekView(String isoWeek, LocalDate weekStart, int posts, List<TotalView> metrics) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeriesRowView(UUID seriesId, String label, int posts, List<TotalView> metrics, List<WeekView> weeks) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeriesComparisonView(LocalDate from, LocalDate to, List<SeriesRowView> series) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SlotView(int slot, int posts, Double median) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EqualAgeView(String metric, long offsetHours, String by, String timeZone, UUID channelId,
                               List<SlotView> slots) {
    }

    /** A stored value with its time: the latest value of a metric, or one raw point. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ValueView(String metric, long value, Instant collectedAt) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SnapshotView(int offsetMinutes, String metric, Long value, Instant asOf, int ageMinutes) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PostAnalyticsView(UUID postId, UUID channelId, String platform, String format, String title, String url,
                                    Instant publishedAt, Instant removedAt, List<ValueView> latest,
                                    List<SnapshotView> snapshots) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PointsView(UUID postId, String metric, List<ValueView> points) {
    }

    public static OverviewView of(AnalyticsReader.Overview overview) {
        return new OverviewView(overview.from(), overview.to(), aggregate(overview.totals(), null),
                rows(overview.channels()), rows(overview.formats()),
                overview.collections().stream()
                        .map(c -> new CollectionView(c.channelId(), c.displayName(), c.platform(),
                                c.status().outcome(), c.status().lastSuccessAt(), c.status().lastFailureAt(),
                                c.status().failureReason()))
                        .toList());
    }

    public static SeriesComparisonView of(AnalyticsReader.SeriesComparison comparison) {
        List<SeriesRowView> rows = new ArrayList<>();
        for (AnalyticsReader.SeriesRow row : comparison.series()) {
            List<WeekView> weeks = new ArrayList<>();
            for (AnalyticsReader.WeekRow week : row.weeks()) {
                weeks.add(new WeekView(week.isoWeek(), week.weekStart(), week.group().posts(),
                        totals(week.group().metrics())));
            }
            rows.add(new SeriesRowView(row.seriesId(), row.label(), row.group().posts(),
                    totals(row.group().metrics()), weeks));
        }
        return new SeriesComparisonView(comparison.from(), comparison.to(), rows);
    }

    public static EqualAgeView of(AnalyticsReader.EqualAge comparison) {
        List<SlotView> slots = comparison.slots().stream()
                .map(s -> new SlotView(s.slot(), s.posts(), s.median()))
                .toList();
        return new EqualAgeView(comparison.metric(), comparison.offset().toHours(),
                comparison.by().name().toLowerCase(Locale.ROOT), PublishSlot.ZONE.getId(), comparison.channelId(), slots);
    }

    public static PostAnalyticsView of(AnalyticsReader.PostDetail detail) {
        ExternalPost post = detail.post();
        return new PostAnalyticsView(post.id(), post.channelId(), post.platform(), post.format(), post.title(),
                post.removed() ? null : post.url(), post.publishedAt(), post.removedAt(),
                values(detail.latest()),
                detail.snapshots().stream().map(AnalyticsViews::snapshot).toList());
    }

    public static PointsView points(UUID postId, String metric, List<PointValue> points) {
        return new PointsView(postId, metric, values(points));
    }

    private static List<AggregateView> rows(List<AnalyticsReader.Row> rows) {
        return rows.stream().map(row -> aggregate(row.group(), row.label())).toList();
    }

    private static AggregateView aggregate(Aggregation.Group group, String label) {
        return new AggregateView(group.key(), label, group.posts(), totals(group.metrics()));
    }

    private static List<TotalView> totals(List<Aggregation.Total> totals) {
        return totals.stream().map(t -> new TotalView(t.name(), t.value(), t.posts(), t.average())).toList();
    }

    private static List<ValueView> values(List<PointValue> values) {
        return values.stream().map(v -> new ValueView(v.metric(), v.value(), v.collectedAt())).toList();
    }

    private static SnapshotView snapshot(SnapshotValue snapshot) {
        return new SnapshotView(snapshot.offsetMinutes(), snapshot.metric(), snapshot.value(), snapshot.asOf(),
                snapshot.ageMinutes());
    }
}
