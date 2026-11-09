package net.onelitefeather.menura.server.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.JsonExceptionHandler;
import io.micronaut.json.JsonSyntaxException;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemDetails;

/**
 * Answers a request body that is not valid JSON as Problem Details. The framework's own answer repeats the raw input in
 * its message (for example the unquoted token that broke the parse), so this handler sends a fixed text instead and
 * never the parser's message.
 */
@Singleton
@Replaces(JsonExceptionHandler.class)
public class MalformedBodyHandler implements ExceptionHandler<JsonSyntaxException, HttpResponse<ProblemResponse>> {

    @Override
    public HttpResponse<ProblemResponse> handle(HttpRequest request, JsonSyntaxException exception) {
        return ProblemResponses.of(request, ProblemDetails.of(400, "Malformed JSON",
                "The request body is not valid JSON.", ProblemResponses.instance(request)));
    }
}
