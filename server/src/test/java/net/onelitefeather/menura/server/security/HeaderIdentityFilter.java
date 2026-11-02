package net.onelitefeather.menura.server.security;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import org.reactivestreams.Publisher;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Test-only stand-in for the authentication filter. Request headers name the caller, and the filter sets the same
 * request attribute that production code sets from a verified web session or token, so every access rule runs for
 * real. It is a filter, not a replacement of the security seam, so the production {@link SessionCurrentPerson} stays
 * the implementation under test.
 *
 * <ul>
 *   <li>{@code X-Test-Person}: the person. A value that is not a UUID is turned into a stable UUID, so tests can name
 *       people in words.</li>
 *   <li>{@code X-Test-Roles}: comma-separated roles; present but empty means signed in with no roles.</li>
 *   <li>{@code X-Test-Channel}: the channel, default web.</li>
 * </ul>
 * Without either header the request is anonymous.
 */
@Filter("/**")
public class HeaderIdentityFilter implements HttpServerFilter {

    public static final String PERSON_HEADER = "X-Test-Person";
    public static final String ROLES_HEADER = "X-Test-Roles";
    public static final String CHANNEL_HEADER = "X-Test-Channel";

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<String> person = request.getHeaders().getFirst(PERSON_HEADER);
        Optional<String> roles = request.getHeaders().getFirst(ROLES_HEADER);
        if (person.isPresent() || roles.isPresent()) {
            UUID personId = person.map(HeaderIdentityFilter::personId).orElseGet(() -> UUID.nameUUIDFromBytes(
                    "default-test-person".getBytes(StandardCharsets.UTF_8)));
            Set<Role> granted = roles.map(HeaderIdentityFilter::rolesOf).orElse(Set.of());
            Channel channel = request.getHeaders().getFirst(CHANNEL_HEADER)
                    .map(value -> Channel.valueOf(value.toUpperCase())).orElse(Channel.WEB);
            request.setAttribute(CurrentPerson.ATTRIBUTE,
                    new CurrentPerson(personId, "test-" + personId, granted, channel));
        }
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }

    private static UUID personId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Set<Role> rolesOf(String header) {
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> Role.parse(name).orElseThrow())
                .collect(Collectors.toUnmodifiableSet());
    }
}
