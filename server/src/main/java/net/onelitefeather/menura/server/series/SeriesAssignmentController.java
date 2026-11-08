package net.onelitefeather.menura.server.series;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.SeriesAssignment;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.series.SeriesViews.AssignRequest;
import net.onelitefeather.menura.server.series.SeriesViews.AssignmentView;

import java.sql.SQLException;
import java.util.UUID;

/** Editors assign an external post to a series, move it, or mark it "no series" (change add-content-series). */
@Controller("/external-posts")
public class SeriesAssignmentController {

    private final SeriesService series;
    private final AuditLog auditLog;
    private final IdentifierGenerator identifiers;

    public SeriesAssignmentController(SeriesService series, AuditLog auditLog, IdentifierGenerator identifiers) {
        this.series = series;
        this.auditLog = auditLog;
        this.identifiers = identifiers;
    }

    @Put("/{id}/series")
    @RequiresRole(Role.EDITOR)
    @ExecuteOn(TaskExecutors.BLOCKING)
    AssignmentView assign(@PathVariable UUID id, @Body AssignRequest body, HttpRequest<?> request) throws SQLException {
        if (body == null) {
            throw new ProblemException(400, "Invalid assignment", "The request names a series or the no-series mark.");
        }
        CurrentPerson person = SeriesController.person(request);
        SeriesAssignment assignment = auditLog.write(SeriesController.caller(person), "series.assign", "external_post",
                RequestIds.from(request, identifiers), connection -> series.assign(connection, id,
                        new AssignmentCommand(body.seriesId(), body.noSeries(), body.replace()), person.personId()));
        return SeriesViews.of(assignment);
    }
}
