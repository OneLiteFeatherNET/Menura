package net.onelitefeather.menura.server.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.core.problem.ProblemException;

/** Maps an expected, client-facing error to its RFC 9457 problem response. */
@Singleton
public class ProblemExceptionHandler implements ExceptionHandler<ProblemException, HttpResponse<ProblemResponse>> {

    @Override
    public HttpResponse<ProblemResponse> handle(HttpRequest request, ProblemException exception) {
        ProblemDetails details = exception.toDetails(ProblemResponses.instance(request));
        return ProblemResponses.of(request, details);
    }
}
