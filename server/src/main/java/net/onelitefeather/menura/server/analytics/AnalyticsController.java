package net.onelitefeather.menura.server.analytics;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.analytics.AnalyticsReader;
import net.onelitefeather.menura.core.analytics.AnalyticsReader.By;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.analytics.AnalyticsViews.EqualAgeView;
import net.onelitefeather.menura.server.analytics.AnalyticsViews.OverviewView;
import net.onelitefeather.menura.server.analytics.AnalyticsViews.PointsView;
import net.onelitefeather.menura.server.analytics.AnalyticsViews.PostAnalyticsView;
import net.onelitefeather.menura.server.analytics.AnalyticsViews.SeriesComparisonView;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Content analytics over REST (change add-content-analytics). Every signed-in role reads them; no endpoint here changes a
 * post, a schedule or an approval. Aggregates come from the stored latest values and snapshots, never from raw points.
 */
@Controller("/analytics")
public class AnalyticsController {

    private static final String DEFAULT_METRIC = "views";
    private static final int DEFAULT_OFFSET_HOURS = 24;
    private static final int DEFAULT_POINTS = 100;

    private final AnalyticsReader reader;

    public AnalyticsController(AnalyticsReader reader) {
        this.reader = reader;
    }

    /** Totals across channels, per channel and per format, with the last collection of every channel. */
    @Get("/overview")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    OverviewView overview(@QueryValue Optional<LocalDate> from, @QueryValue Optional<LocalDate> to,
                          HttpRequest<?> request) throws SQLException {
        Set<Role> roles = person(request).roles();
        LocalDate[] period = period(from, to);
        return AnalyticsViews.of(reader.overview(period[0], period[1], roles));
    }

    /** The series comparison per week, ranked by engaged views. */
    @Get("/series")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    SeriesComparisonView series(@QueryValue Optional<LocalDate> from, @QueryValue Optional<LocalDate> to,
                                HttpRequest<?> request) throws SQLException {
        Set<Role> roles = person(request).roles();
        LocalDate[] period = period(from, to);
        return AnalyticsViews.of(reader.series(period[0], period[1], roles));
    }

    /** The median of one metric per publish hour or weekday, at equal age. */
    @Get("/equal-age")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    EqualAgeView equalAge(@QueryValue Optional<String> metric, @QueryValue Optional<Integer> offsetHours,
                          @QueryValue Optional<String> by, @QueryValue Optional<UUID> channelId) throws SQLException {
        By grouping = By.parse(by.orElse("hour")).orElseThrow(() -> new ProblemException(400, "Invalid grouping",
                "The grouping must be hour or weekday."));
        AnalyticsReader.EqualAge comparison = reader.equalAge(metric.orElse(DEFAULT_METRIC),
                Duration.ofHours(offsetHours.orElse(DEFAULT_OFFSET_HOURS)), grouping, channelId.orElse(null));
        return AnalyticsViews.of(comparison);
    }

    /** The latest values and the snapshots of one external post; a post the caller may not see answers 404. */
    @Get("/external-posts/{id}")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostAnalyticsView post(@PathVariable UUID id) throws SQLException {
        return AnalyticsViews.of(reader.post(id));
    }

    /** The raw points of one external post, optionally of one metric, in collection-time order. */
    @Get("/external-posts/{id}/points")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    PointsView points(@PathVariable UUID id, @QueryValue Optional<String> metric,
                      @QueryValue Optional<Integer> limit) throws SQLException {
        String name = metric.orElse(null);
        return AnalyticsViews.points(id, name, reader.points(id, name, limit.orElse(DEFAULT_POINTS)));
    }

    /** The period of a request: the last 90 days ending today when no date is given. */
    private LocalDate[] period(Optional<LocalDate> from, Optional<LocalDate> to) {
        LocalDate end = to.orElseGet(reader::today);
        LocalDate start = from.orElseGet(() -> end.minusDays(AnalyticsReader.DEFAULT_DAYS - 1L));
        return new LocalDate[] {start, end};
    }

    private static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }
}
