package net.onelitefeather.menura.server.auth;

import io.micronaut.http.HttpRequest;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.audit.Channel;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The one request-scoped identity: the verified person of a request, with the roles verified for it and the channel
 * it came in on. The authentication filter sets it from a web session (channel {@link Channel#WEB}) or from a verified
 * Menura access token. A request without it is anonymous and fails closed in the access filter.
 *
 * <p>{@code personId} is Menura's identifier of the person; {@code subject} is the upstream Entra ID subject, which
 * is never used as a key in Menura's own data. Never build one from unverified input, and never log the subject
 * together with a token.
 */
public record CurrentPerson(UUID personId, String subject, Set<Role> roles, Channel channel) {

    /** Request attribute under which the authentication filter stores the verified person. */
    public static final String ATTRIBUTE = CurrentPerson.class.getName();

    public CurrentPerson {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(channel, "channel");
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
