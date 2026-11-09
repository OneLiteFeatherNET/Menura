package net.onelitefeather.menura.server.attribution;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.attribution.JoinReports;
import net.onelitefeather.menura.core.attribution.JoinService;
import net.onelitefeather.menura.core.attribution.JoinSettings;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.attribution.JoinViews.NewPlayersView;
import net.onelitefeather.menura.server.attribution.JoinViews.PostEstimateView;
import net.onelitefeather.menura.server.attribution.JoinViews.SeriesEstimatesView;
import net.onelitefeather.menura.server.attribution.JoinViews.SettingsRequest;
import net.onelitefeather.menura.server.attribution.JoinViews.SettingsView;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The join attribution for people (change add-join-attribution): the join domain and the attribution window, which only an
 * admin reads and sets, and the reports every signed-in role reads. A post the caller may not see answers 404.
 */
@Controller("/join-attribution")
public class JoinAttributionController {

    private static final int DEFAULT_PERIOD_DAYS = 30;

    private final JoinService joins;

    public JoinAttributionController(JoinService joins) {
        this.joins = joins;
    }

    @Get("/settings")
    @RequiresRole(Role.ADMIN)
    @ExecuteOn(TaskExecutors.BLOCKING)
    SettingsView settings() throws SQLException {
        return SettingsView.of(joins.settings());
    }

    /** Saves the join domain and, when given, the window. The domain is refused with 422 unless it is a lower-case host name. */
    @Put("/settings")
    @RequiresRole(Role.ADMIN)
    @ExecuteOn(TaskExecutors.BLOCKING)
    SettingsView saveSettings(@Body SettingsRequest body, HttpRequest<?> request) throws SQLException {
        UUID actor = person(request).personId();
        JoinSettings saved = joins.saveSettings(actor, body == null ? null : body.joinDomain(),
                body == null ? null : body.attributionWindowHours());
        return SettingsView.of(saved);
    }

    /** New players per day or per ISO week: totals, the stated sources, the unanswered joins and the answer rate. */
    @Get("/new-players")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    NewPlayersView newPlayers(@QueryValue Optional<LocalDate> from, @QueryValue Optional<LocalDate> to,
                              @QueryValue Optional<String> grouping) throws SQLException {
        JoinReports.Grouping by = JoinReports.Grouping.parse(grouping.orElse("day")).orElseThrow(
                () -> new ProblemException(400, "Invalid grouping", "The grouping must be day or week."));
        LocalDate end = to.orElseGet(joins::today);
        LocalDate start = from.orElseGet(() -> end.minusDays(DEFAULT_PERIOD_DAYS - 1L));
        return JoinViews.of(joins.newPlayers(start, end, by));
    }

    /** The estimated first joins of one post; a post whose series the caller may not see answers 404. */
    @Get("/posts/{id}")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostEstimateView postEstimate(@PathVariable UUID id, HttpRequest<?> request) throws SQLException {
        JoinReports.PostEstimate estimate = joins.postEstimate(id, person(request).roles());
        return new PostEstimateView(estimate.postId(), estimate.estimatedFirstJoins(), estimate.windowHours(),
                JoinReports.ESTIMATE_LABEL);
    }

    /** The estimated first joins per series for the joins of a period; posts without a series are "no series". */
    @Get("/series")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    SeriesEstimatesView series(@QueryValue Optional<LocalDate> from, @QueryValue Optional<LocalDate> to,
                               HttpRequest<?> request) throws SQLException {
        LocalDate end = to.orElseGet(joins::today);
        LocalDate start = from.orElseGet(() -> end.minusDays(DEFAULT_PERIOD_DAYS - 1L));
        return JoinViews.of(joins.seriesEstimates(start, end, person(request).roles()));
    }

    private static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }
}
