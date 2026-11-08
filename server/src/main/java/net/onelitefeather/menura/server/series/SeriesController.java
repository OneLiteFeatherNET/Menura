package net.onelitefeather.menura.server.series;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.ContentSeries;
import net.onelitefeather.menura.core.series.SeriesAssignment;
import net.onelitefeather.menura.core.series.SeriesReports;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.series.SeriesViews.OverviewView;
import net.onelitefeather.menura.server.series.SeriesViews.SeriesDetailView;
import net.onelitefeather.menura.server.series.SeriesViews.SeriesRequest;
import net.onelitefeather.menura.server.series.SeriesViews.SeriesView;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The content series over REST. Every signed-in role reads the catalogue, the overview and the episodes; only the editor
 * role writes a series. Each write commits with its audit entry.
 */
@Controller("/series")
public class SeriesController {

    private final SeriesService series;
    private final AuditLog auditLog;
    private final Transactions transactions;
    private final IdentifierGenerator identifiers;

    public SeriesController(SeriesService series, AuditLog auditLog, Transactions transactions,
                            IdentifierGenerator identifiers) {
        this.series = series;
        this.auditLog = auditLog;
        this.transactions = transactions;
        this.identifiers = identifiers;
    }

    /** The series the caller may see. */
    @Get
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    List<SeriesView> list(HttpRequest<?> request) throws SQLException {
        Set<Role> roles = person(request).roles();
        return transactions.inTransaction(c -> series.list(c, roles).stream().map(SeriesViews::of).toList());
    }

    /** The series the caller may see, with their warnings and the weekly coverage of every channel. */
    @Get("/overview")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    OverviewView overview(@QueryValue Optional<Integer> weeks, HttpRequest<?> request) throws SQLException {
        Set<Role> roles = person(request).roles();
        SeriesReports.Overview overview = transactions.inTransaction(c -> series.overview(c, weeks.orElse(null), roles));
        return SeriesViews.of(overview);
    }

    /** One series with its episodes in number order; a series the caller may not see answers 404. */
    @Get("/{id}")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    SeriesDetailView detail(@PathVariable UUID id, HttpRequest<?> request) throws SQLException {
        Set<Role> roles = person(request).roles();
        return transactions.inTransaction(c -> {
            ContentSeries found = series.find(c, id, roles);
            List<SeriesViews.EpisodeView> episodes = new ArrayList<>();
            for (SeriesReports.Episode episode : series.episodes(c, id)) {
                SeriesAssignment assignment = series.assignmentOf(c, episode.post().id()).orElse(null);
                episodes.add(SeriesViews.episode(episode, assignment));
            }
            return new SeriesDetailView(SeriesViews.of(found), episodes);
        });
    }

    @Post
    @RequiresRole(Role.EDITOR)
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<SeriesView> create(@Body SeriesRequest body, HttpRequest<?> request) throws SQLException {
        CurrentPerson person = person(request);
        ContentSeries created = auditLog.write(caller(person), "series.create", "content_series",
                RequestIds.from(request, identifiers), connection -> series.create(connection, SeriesViews.draft(require(body))));
        return HttpResponse.created(SeriesViews.of(created)).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Put("/{id}")
    @RequiresRole(Role.EDITOR)
    @ExecuteOn(TaskExecutors.BLOCKING)
    SeriesView update(@PathVariable UUID id, @Body SeriesRequest body, HttpRequest<?> request) throws SQLException {
        CurrentPerson person = person(request);
        ContentSeries updated = auditLog.write(caller(person), "series.update", "content_series",
                RequestIds.from(request, identifiers),
                connection -> series.update(connection, id, SeriesViews.draft(require(body)), person.roles()));
        return SeriesViews.of(updated);
    }

    static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }

    static Caller caller(CurrentPerson person) {
        return new Caller(person.personId(), person.channel());
    }

    static SeriesRequest require(SeriesRequest body) {
        if (body == null) {
            throw new ProblemException(400, "Invalid series", "The request body must be a series.");
        }
        return body;
    }
}
