package net.onelitefeather.menura.server.prototype.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.Model;
import net.onelitefeather.menura.server.prototype.analytics.Aggregation.Contribution;
import net.onelitefeather.menura.server.prototype.analytics.Aggregation.Selection;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Metrics, aggregates, the weekday-hour heatmap and per-post snapshots of the prototype. Reads need any signed-in role. */
@Controller("/api/prototype/analytics")
@Requires(env = "prototype")
public class PrototypeAnalyticsController {

    private final DemoStore store;
    private final Clock clock;

    public PrototypeAnalyticsController(DemoStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Get("/metrics")
    @RequiresRole
    List<MetricCatalogue.Metric> metrics() {
        return MetricCatalogue.METRICS;
    }

    @Get("/aggregate")
    @RequiresRole
    Aggregation.Result aggregate(@QueryValue Optional<String> metric, @QueryValue Optional<String> channelIds,
                                 @QueryValue Optional<String> groupBy, @QueryValue Optional<String> offset,
                                 @QueryValue Optional<String> from, @QueryValue Optional<String> to) {
        Aggregation.GroupBy grouping = Aggregation.GroupBy.parse(groupBy.orElse("day"));
        Selection selection = Aggregation.select(store.channels(), metric.orElse(null), channelIds.orElse(null),
                offset.orElse(null), from.orElse(null), to.orElse(null));
        List<Contribution> contributions = Aggregation.contributions(selection, store.posts(null, null), now());
        List<String> seriesOrder = store.series().stream().map(Model.Series::id).toList();
        return new Aggregation.Result(selection.metric().id(), grouping.wire(), selection.offset().label(),
                Aggregation.rows(selection, grouping, contributions, seriesOrder), Aggregation.missing(selection));
    }

    @Get("/heatmap")
    @RequiresRole
    Aggregation.Heatmap heatmap(@QueryValue Optional<String> metric, @QueryValue Optional<String> channelIds,
                                @QueryValue Optional<String> offset) {
        Selection selection = Aggregation.select(store.channels(), metric.orElse(null), channelIds.orElse(null),
                offset.orElse(null), null, null);
        return Aggregation.heatmap(selection, Aggregation.contributions(selection, store.posts(null, null), now()));
    }

    /** The measurements of one post that have happened by now. The offsets in the future are not served. */
    @Get("/posts/{postId}/snapshots")
    @RequiresRole
    List<SnapshotView> snapshots(@PathVariable String postId) {
        Model.Post post = store.posts(null, null).stream()
                .filter(candidate -> candidate.id().equals(postId))
                .findFirst()
                .orElseThrow(() -> new ProblemException(404, "Not Found", "unbekannter Beitrag."));
        return Aggregation.available(SnapshotSeed.of(post), now()).stream().map(SnapshotView::of).toList();
    }

    private Instant now() {
        return Instant.now(clock);
    }

    /** One measurement as the calendar shows it. Counts that the platform does not deliver are null, never 0. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SnapshotView(String offset, String at, long views, Long engagedViews, long likes, long comments,
                               long shares) {

        static SnapshotView of(Snapshot snapshot) {
            return new SnapshotView(snapshot.offset().label(), snapshot.at().toString(),
                    snapshot.value("views").orElseThrow().longValue(),
                    snapshot.value("engagedViews").map(Double::longValue).orElse(null),
                    snapshot.value("likes").orElseThrow().longValue(),
                    snapshot.value("comments").orElseThrow().longValue(),
                    snapshot.value("shares").orElseThrow().longValue());
        }
    }
}
