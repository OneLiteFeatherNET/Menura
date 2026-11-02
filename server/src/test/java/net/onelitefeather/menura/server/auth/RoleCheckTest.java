package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleCheckTest {

    private static final CurrentPerson VIEWER = new CurrentPerson("p-1", Set.of(Role.VIEWER));
    private static final CurrentPerson EDITOR = new CurrentPerson("p-2", Set.of(Role.EDITOR));
    private static final CurrentPerson NO_ROLES = new CurrentPerson("p-3", Set.of());

    @Test
    void viewerIsRefusedWith403ForAnEditorEndpoint() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> RoleCheck.require(Optional.of(VIEWER), Role.EDITOR),
                "a viewer must not pass an endpoint that needs the editor role");

        assertEquals(403, refused.status());
    }

    @Test
    void missingPersonIsRefusedWith401() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> RoleCheck.require(Optional.empty(), Role.EDITOR),
                "a request without a verified person must not pass a protected endpoint");

        assertEquals(401, refused.status());
    }

    @Test
    void anyOneOfTheDeclaredRolesIsEnough() {
        assertDoesNotThrow(() -> RoleCheck.require(Optional.of(EDITOR), Role.EDITOR, Role.ADMIN),
                "an endpoint that lists several roles accepts a person holding any one of them");
    }

    @Test
    void endpointWithoutRolesAcceptsAnySignedInPerson() {
        assertDoesNotThrow(() -> RoleCheck.require(Optional.of(NO_ROLES)),
                "an endpoint that declares no role only needs a signed-in person");
    }

    @Test
    void personWithoutRolesIsRefusedWith403WhenRolesAreRequired() {
        ProblemException refused = assertThrows(ProblemException.class,
                () -> RoleCheck.require(Optional.of(NO_ROLES), Role.VIEWER));

        assertEquals(403, refused.status());
    }
}
