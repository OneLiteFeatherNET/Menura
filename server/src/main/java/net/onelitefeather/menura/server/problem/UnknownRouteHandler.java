package net.onelitefeather.menura.server.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import net.onelitefeather.menura.core.problem.ProblemDetails;

/** Answers a request for a route that does not exist as Problem Details, not as the framework's default page. */
@Controller
public class UnknownRouteHandler {

    @Error(status = HttpStatus.NOT_FOUND, global = true)
    public HttpResponse<ProblemResponse> notFound(HttpRequest<?> request) {
        return ProblemResponses.of(request, ProblemDetails.of(404, "Not Found",
                "No resource exists at this address.", ProblemResponses.instance(request)));
    }
}
