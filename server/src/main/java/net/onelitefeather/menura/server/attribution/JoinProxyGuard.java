package net.onelitefeather.menura.server.attribution;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.problem.ProblemResponse;
import net.onelitefeather.menura.server.problem.ProblemResponses;
import net.onelitefeather.menura.server.web.PersonRateLimiter;

import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Admits the network proxy to the join endpoints (change add-join-attribution). The proxy is a machine client: it presents
 * its own credential as a bearer secret, and the server compares the secret's SHA-256 digest with the configured digest in
 * constant time. The configured value is the digest, so the server holds no secret that could be presented.
 *
 * <p>The checks run in this order: a missing or wrong credential answers 401; a signed-in person without the proxy
 * credential answers 403; a valid credential over its request budget answers 429 with Retry-After. Without a configured
 * digest every call answers 401, so the endpoints stay closed until the operator configures the proxy.
 */
@Singleton
public class JoinProxyGuard {

    /** The proxy's requests per minute. One join is one request, so this allows bursts well above the network's rate. */
    static final int REQUESTS_PER_MINUTE = 600;

    private static final String BEARER = "Bearer ";
    private static final String BUDGET_KEY = "join-proxy";

    private final byte[] expectedDigest;
    private final PersonRateLimiter limiter;

    public JoinProxyGuard(@Value("${menura.join.proxy-credential-hash:}") String configuredDigest, Clock clock) {
        this.expectedDigest = digestOf(configuredDigest);
        this.limiter = new PersonRateLimiter(clock, REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
    }

    /**
     * The refusal to send for this request, or null when the request comes from the proxy and is within its budget.
     */
    public MutableHttpResponse<?> refusalFor(HttpRequest<?> request) {
        if (presentedCredentialMatches(request)) {
            PersonRateLimiter.Decision decision = limiter.tryAcquire(BUDGET_KEY);
            if (decision.allowed()) {
                return null;
            }
            return tooManyRequests(request, decision.retryAfterSeconds());
        }
        if (CurrentPerson.of(request).isPresent()) {
            return ProblemResponses.of(request, ProblemDetails.of(403, "Forbidden",
                    "A person may not send join events; only the network proxy may.", request.getPath()));
        }
        return ProblemResponses.of(request, ProblemDetails.of(401, "Unauthorized",
                "The request carries no valid credential of the network proxy.", request.getPath()));
    }

    private boolean presentedCredentialMatches(HttpRequest<?> request) {
        if (expectedDigest == null) {
            return false;
        }
        Optional<String> presented = request.getHeaders().getAuthorization()
                .filter(value -> value.regionMatches(true, 0, BEARER, 0, BEARER.length()))
                .map(value -> value.substring(BEARER.length()).trim())
                .filter(value -> !value.isEmpty());
        return presented.isPresent() && SecretHasher.matches(presented.get(), expectedDigest);
    }

    private static MutableHttpResponse<?> tooManyRequests(HttpRequest<?> request, long retryAfterSeconds) {
        ProblemDetails details = ProblemDetails.of(429, "Too Many Requests",
                "The request budget of the network proxy is used up. Retry after the number of seconds in Retry-After.",
                request.getPath());
        return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .contentType(MediaType.of(ProblemDetails.MEDIA_TYPE))
                .body(ProblemResponse.from(details));
    }

    /** The configured digest as bytes; empty means not configured. A value that is not 64 hex digits stops the start. */
    static byte[] digestOf(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String value = configured.trim();
        if (!value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("MENURA_JOIN_PROXY_CREDENTIAL_HASH must be the SHA-256 hex digest of the proxy "
                    + "credential, 64 hex digits");
        }
        return HexFormat.of().parseHex(value.toLowerCase(Locale.ROOT));
    }
}
