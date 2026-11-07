package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.JsonExceptionHandler;
import io.micronaut.json.JsonSyntaxException;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.server.problem.ProblemResponse;
import net.onelitefeather.menura.server.problem.ProblemResponses;

/**
 * Answers a body that is not valid JSON with a 400 problem that does not repeat the body. Micronaut's own handler
 * quotes the unreadable token in its message, so the prototype replaces it. Active only in the prototype environment.
 */
@Singleton
@Replaces(JsonExceptionHandler.class)
@Requires(env = "prototype")
public class PrototypeJsonErrorHandler implements ExceptionHandler<JsonSyntaxException, HttpResponse<ProblemResponse>> {

    @Override
    public HttpResponse<ProblemResponse> handle(HttpRequest request, JsonSyntaxException exception) {
        ProblemDetails details = ProblemDetails.of(400, "Bad Request",
                "The request body is not valid JSON.", ProblemResponses.instance(request));
        return ProblemResponses.of(request, details);
    }
}
