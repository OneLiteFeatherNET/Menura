package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Optional;
import java.util.Set;

/** The decision of the role check, kept free of HTTP so it can be tested on its own. */
public final class RoleCheck {

    private RoleCheck() {
    }

    /**
     * Passes when the caller is a verified person holding at least one of the wanted roles, or when no role is
     * wanted and the caller is verified. Otherwise throws a Problem: 401 for no verified person, 403 for a person
     * without the wanted roles.
     */
    public static void require(Optional<CurrentPerson> person, Role... wanted) {
        CurrentPerson caller = person.orElseThrow(() -> new ProblemException(401, "Unauthorized",
                "Sign in to use this endpoint."));
        if (wanted.length == 0) {
            return;
        }
        if (!caller.hasAnyRole(Set.of(wanted))) {
            throw new ProblemException(403, "Forbidden", "Your roles do not allow this action.");
        }
    }
}
