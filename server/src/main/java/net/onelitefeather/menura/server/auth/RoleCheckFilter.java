package net.onelitefeather.menura.server.auth;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.RouteMatch;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.problem.ProblemResponse;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Enforces {@link RequiresRole} on the matched route. It runs in the security phase, before the controller is
 * invoked, so a refused request never reaches its handler. A refusal is answered directly as Problem Details.
 */
@Filter(Filter.MATCH_ALL_PATTERN)
public class RoleCheckFilter implements HttpServerFilter {

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<Role[]> declared = declaredRoles(request);
        if (declared.isEmpty()) {
            return chain.proceed(request);
        }
        try {
            RoleCheck.require(CurrentPerson.of(request), declared.get());
        } catch (ProblemException refused) {
            return Publishers.just(problemResponse(refused, request));
        }
        return chain.proceed(request);
    }

    /** Runs after the authentication filter, which shares the security phase and comes first at its order. */
    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() + 10;
    }

    /** The roles declared on the matched route, or empty when the route carries no {@link RequiresRole}. */
    @SuppressWarnings("removal")
    static Optional<Role[]> declaredRoles(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class)
                .map(RouteMatch::getAnnotationMetadata)
                .filter(metadata -> metadata.hasAnnotation(RequiresRole.class))
                .map(metadata -> metadata.enumValues(RequiresRole.class, Role.class));
    }

    private static MutableHttpResponse<?> problemResponse(ProblemException refused, HttpRequest<?> request) {
        ProblemDetails details = refused.toDetails(request.getPath());
        return HttpResponse.status(HttpStatus.valueOf(details.status()))
                .contentType(MediaType.of(ProblemDetails.MEDIA_TYPE))
                .body(ProblemResponse.from(details));
    }
}
