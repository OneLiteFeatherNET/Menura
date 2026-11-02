package net.onelitefeather.menura.server.auth;

import io.micronaut.http.HttpRequest;
import net.onelitefeather.menura.core.auth.Role;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The verified caller of a request: the person's stable subject and the roles that were verified with it.
 * Set by the authentication filter; a request without it is anonymous.
 *
 * <p>For other components: read it with {@link #of(HttpRequest)} in a handler or filter. Never build one from
 * unverified input, and never log the subject together with a token.
 */
public record CurrentPerson(String subject, Set<Role> roles) {

    /** Request attribute under which the authentication filter stores the verified person. */
    public static final String ATTRIBUTE = CurrentPerson.class.getName();

    public CurrentPerson {
        Objects.requireNonNull(subject, "subject");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    /** The verified person of a request, or empty when the caller is anonymous. */
    public static Optional<CurrentPerson> of(HttpRequest<?> request) {
        return request.getAttribute(ATTRIBUTE, CurrentPerson.class);
    }

    public boolean hasAnyRole(Set<Role> wanted) {
        return wanted.stream().anyMatch(roles::contains);
    }
}
