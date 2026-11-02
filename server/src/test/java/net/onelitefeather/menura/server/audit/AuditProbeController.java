package net.onelitefeather.menura.server.audit;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.security.CurrentPerson;

import java.sql.SQLException;

/**
 * Test-only write endpoint. Its write is an audited write like every other: the audit entry is recorded in the same
 * transaction, and the endpoint needs a signed-in caller.
 */
@Controller("/test-audit")
class AuditProbeController {

    private final CurrentPerson currentPerson;
    private final AuditLog auditLog;
    private final IdentifierGenerator identifiers;

    AuditProbeController(CurrentPerson currentPerson, AuditLog auditLog, IdentifierGenerator identifiers) {
        this.currentPerson = currentPerson;
        this.auditLog = auditLog;
        this.identifiers = identifiers;
    }

    @Post("/write")
    HttpResponse<Void> write(HttpRequest<?> request) throws SQLException {
        Caller caller = currentPerson.resolve(request)
                .orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
        auditLog.write(caller, "test.write", "probe", RequestIds.from(request, identifiers), connection -> null);
        return HttpResponse.noContent();
    }
}
