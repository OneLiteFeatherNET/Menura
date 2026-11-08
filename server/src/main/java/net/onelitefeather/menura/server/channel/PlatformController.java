package net.onelitefeather.menura.server.channel;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Actor;
import net.onelitefeather.menura.core.channel.AuditStatus;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.PlatformSnapshot;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.channel.ChannelViews.AuditStatusRequest;
import net.onelitefeather.menura.server.channel.ChannelViews.AuditView;
import net.onelitefeather.menura.server.channel.ChannelViews.PlatformView;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.security.CurrentPerson;

import java.sql.SQLException;
import java.util.List;

/**
 * The platforms of this build and the audit status of their apps. Every signed-in person reads them; only an
 * administrator records an audit status, because it decides whether public visibility may be used.
 */
@Controller("/platforms")
public class PlatformController {

    private final ConnectionService connections;
    private final CurrentPerson currentPerson;
    private final IdentifierGenerator identifiers;

    public PlatformController(ConnectionService connections, CurrentPerson currentPerson, IdentifierGenerator identifiers) {
        this.connections = connections;
        this.currentPerson = currentPerson;
        this.identifiers = identifiers;
    }

    @Get
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    List<PlatformView> platforms() throws SQLException {
        return connections.platforms().stream().map(ChannelViews::of).toList();
    }

    /** Records the audit status of a platform app with its source; the decision is dated and audited. */
    @Put("/{platform}/audit-status")
    @RequiresRole({Role.ADMIN})
    @ExecuteOn(TaskExecutors.BLOCKING)
    AuditView recordAuditStatus(HttpRequest<?> request, @PathVariable String platform,
                                @Body AuditStatusRequest body) throws SQLException {
        if (body == null || body.status() == null) {
            throw new ProblemException(400, "Bad Request", "The audit status is required.");
        }
        AuditStatus status;
        try {
            status = AuditStatus.parse(body.status());
        } catch (IllegalArgumentException unknown) {
            throw new ProblemException(400, "Bad Request", "The audit status must be unknown, unaudited or audited.");
        }
        Caller caller = currentPerson.require(request);
        Actor actor = new Actor(caller.personId(), caller.channel().value(), RequestIds.from(request, identifiers));
        connections.setAuditStatus(actor, platform, status, body.source());
        PlatformSnapshot updated = connections.platforms().stream()
                .filter(snapshot -> snapshot.platform().equals(platform)).findFirst()
                .orElseThrow(() -> new ProblemException(400, "Bad Request", "The platform is not supported."));
        return ChannelViews.auditOf(updated.audit());
    }
}
