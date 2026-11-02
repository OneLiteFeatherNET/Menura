package net.onelitefeather.menura.server.problem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import net.onelitefeather.menura.core.problem.ProblemDetails;

import java.util.regex.Pattern;

/** Builds the RFC 9457 response of every refusal, and the instance it names, without leaking a secret from the path. */
public final class ProblemResponses {

    private static final Pattern CALENDAR_FEED_TOKEN = Pattern.compile("^(/calendar/feed/)[^/]+");

    private ProblemResponses() {
    }

    public static MutableHttpResponse<ProblemResponse> of(HttpRequest<?> request, ProblemDetails details) {
        MutableHttpResponse<ProblemResponse> response = HttpResponse.status(HttpStatus.valueOf(details.status()))
                .contentType(MediaType.of(ProblemDetails.MEDIA_TYPE))
                .body(ProblemResponse.from(details));
        if (details.status() == HttpStatus.UNAUTHORIZED.getCode()) {
            response.header("WWW-Authenticate", "Bearer");
        }
        return response;
    }

    /**
     * The path of the request, with the calendar feed token replaced by a placeholder. The token is a credential, so
     * it never appears in a problem body, in a log line or in an error report.
     */
    public static String instance(HttpRequest<?> request) {
        return CALENDAR_FEED_TOKEN.matcher(request.getPath()).replaceFirst("$1{token}");
    }
}
