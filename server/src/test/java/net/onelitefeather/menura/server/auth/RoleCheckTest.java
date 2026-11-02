package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleCheckTest {

    private static final UUID PERSON_1 = UUID.fromString("1b4e28ba-2fa1-4d3b-8c2e-7f9a1d3e5b01");
    private static final UUID PERSON_2 = UUID.fromString("2c5f39cb-3ab2-4e4c-9d3f-8a0b2e4f6c02");
    private static final UUID PERSON_3 = UUID.fromString("3d6a4adc-4bc3-4f5d-8e4a-9b1c3f5a7d03");
    private static final CurrentPerson VIEWER = new CurrentPerson(PERSON_1, "p-1", Set.of(Role.VIEWER), Channel.WEB);
    private static final CurrentPerson EDITOR = new CurrentPerson(PERSON_2, "p-2", Set.of(Role.EDITOR), Channel.WEB);
    private static final CurrentPerson NO_ROLES = new CurrentPerson(PERSON_3, "p-3", Set.of(), Channel.WEB);

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
