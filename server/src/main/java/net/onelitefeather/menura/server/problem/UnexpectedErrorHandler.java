package net.onelitefeather.menura.server.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Last-resort mapping for errors nobody expected. The client gets a generic 500 problem; the cause is logged
 * on the server only, so internal messages never leave the service.
 */
@Singleton
public class UnexpectedErrorHandler implements ExceptionHandler<Throwable, HttpResponse<ProblemResponse>> {

    private static final Logger LOG = LoggerFactory.getLogger(UnexpectedErrorHandler.class);

    @Override
    public HttpResponse<ProblemResponse> handle(HttpRequest request, Throwable exception) {
        String instance = ProblemResponses.instance(request);
        LOG.error("Unexpected error while handling {}", instance, exception);
        ProblemDetails details = ProblemDetails.of(500, "Internal Server Error",
                "An unexpected error occurred.", instance);
        return ProblemResponses.of(request, details);
    }
}
