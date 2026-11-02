package net.onelitefeather.menura.server.audit;

import io.micronaut.http.HttpRequest;
import net.onelitefeather.menura.core.id.IdentifierGenerator;

import java.util.regex.Pattern;

/**
 * The request id stored with each audit entry. It comes from the {@code X-Request-Id} header when the header is a
 * short token; anything else is replaced by a fresh identifier, so a client cannot write arbitrary text into the log.
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIds() {
    }

    public static String from(HttpRequest<?> request, IdentifierGenerator identifiers) {
        return request.getHeaders().getFirst(HEADER)
                .filter(value -> VALID.matcher(value).matches())
                .orElseGet(() -> identifiers.next().toString());
    }
}
