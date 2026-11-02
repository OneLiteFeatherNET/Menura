package net.onelitefeather.menura.server.auth;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import net.onelitefeather.menura.core.auth.Role;
import org.reactivestreams.Publisher;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test-only stand-in for the authentication filter: a request header names the roles of the caller, so the role
 * check can be exercised without tokens. Production code sets the same request attribute from a verified token.
 */
@Filter("/test-auth/**")
class TestPersonFilter implements HttpServerFilter {

    static final String ROLES_HEADER = "X-Test-Roles";

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        request.getHeaders().getFirst(ROLES_HEADER).ifPresent(header -> {
            Set<Role> roles = Arrays.stream(header.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .map(name -> Role.parse(name).orElseThrow())
                    .collect(Collectors.toUnmodifiableSet());
            request.setAttribute(CurrentPerson.ATTRIBUTE, new CurrentPerson("test-person", roles));
        });
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }
}
