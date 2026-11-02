package net.onelitefeather.menura.server.security;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.annotation.Filter;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.server.problem.ProblemResponses;
import org.reactivestreams.Publisher;

import java.util.Set;

/**
 * Default-deny access rule, second in the filter order after authentication (see
 * {@code auth.AuthenticationFilter}). Only the anonymous allow-list passes without a person, and a route marked
 * {@link SelfAuthenticated} verifies its own credential. Every other request needs a signed-in caller from
 * {@link CurrentPerson}; without one it answers 401 as Problem Details before the role check or the person budget
 * runs and before any handler runs.
 */
@Singleton
@Filter(Filter.MATCH_ALL_PATTERN)
public class AccessFilter implements HttpServerFilter {

    /**
     * The only anonymous routes: liveness and readiness, the OpenAPI document, and the web sign-in endpoints, which
     * exist to create or end a session and so cannot require one.
     */
    static final Set<String> ANONYMOUS_PATHS = Set.of("/health", "/openapi/menura.yaml", "/auth/login",
            "/auth/callback", "/auth/logout");

    private final CurrentPerson currentPerson;

    public AccessFilter(CurrentPerson currentPerson) {
        this.currentPerson = currentPerson;
    }

    /** Runs after the authentication filter, which sets the person this filter reads. */
    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() + 10;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (ANONYMOUS_PATHS.contains(request.getPath()) || isSelfAuthenticated(request)) {
            return chain.proceed(request);
        }
        if (currentPerson.resolve(request).isPresent()) {
            return chain.proceed(request);
        }
        MutableHttpResponse<?> refused = ProblemResponses.of(request, ProblemDetails.of(401, "Unauthorized",
                "Sign in to continue.", ProblemResponses.instance(request)));
        return Publishers.just(refused);
    }

    // The route match is the attribute Micronaut sets before the filters run; the constant is deprecated for removal
    // in Micronaut 5 but still carries it, and there is no other route accessor for a server filter.
    @SuppressWarnings("removal")
    private static boolean isSelfAuthenticated(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class)
                .map(match -> ((RouteMatch<?>) match).getAnnotationMetadata().hasAnnotation(SelfAuthenticated.class))
                .orElse(false);
    }
}
