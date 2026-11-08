package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.auth.Role;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The person behind a post action: the person, the roles verified for the request and whether the request came from the
 * web application's own session. Approval and the acceptance of a revision require the publisher role and an interactive
 * session; a token or an MCP client never has one.
 */
public record PostActor(UUID personId, Set<Role> roles, boolean interactive) {

    public PostActor {
        Objects.requireNonNull(personId, "personId");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
