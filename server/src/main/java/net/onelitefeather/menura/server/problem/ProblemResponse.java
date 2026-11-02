package net.onelitefeather.menura.server.problem;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.problem.ProblemDetails;

/** JSON form of {@link ProblemDetails}, serialised with the Micronaut serde processor. */
@Serdeable
public record ProblemResponse(String type, String title, int status, String detail, String instance) {

    public static ProblemResponse from(ProblemDetails details) {
        return new ProblemResponse(details.type(), details.title(), details.status(), details.detail(),
                details.instance());
    }
}
