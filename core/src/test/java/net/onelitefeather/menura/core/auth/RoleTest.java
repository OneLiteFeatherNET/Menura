package net.onelitefeather.menura.core.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoleTest {

    @Test
    void parsesTheFiveRoleNamesCaseInsensitively() {
        assertEquals(Optional.of(Role.PUBLISHER), Role.parse("publisher"));
        assertEquals(Optional.of(Role.ADMIN), Role.parse("ADMIN"));
    }

    @Test
    void unknownRoleNameIsNotGranted() {
        assertEquals(Optional.empty(), Role.parse("superuser"),
                "a name that is not one of the five roles must grant nothing");
    }

    @Test
    void theSetOfRolesIsExactlyTheFiveNamedInTheSpecification() {
        assertEquals(5, Role.values().length);
        assertTrue(Role.parse("viewer").isPresent());
        assertTrue(Role.parse("editor").isPresent());
        assertTrue(Role.parse("operator").isPresent());
        assertFalse(Role.parse("owner").isPresent());
    }
}
