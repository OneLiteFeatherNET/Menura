package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleMappingTest {

    private static final String VIEWER_GROUP = "11111111-1111-4111-8111-111111111111";
    private static final String EDITOR_GROUP = "22222222-2222-4222-8222-222222222222";
    private static final String ADMIN_GROUP = "33333333-3333-4333-8333-333333333333";

    private final RoleMapping mapping = new RoleMapping(Map.of(
            Role.VIEWER, Set.of(VIEWER_GROUP),
            Role.EDITOR, Set.of(EDITOR_GROUP),
            Role.ADMIN, Set.of(ADMIN_GROUP)));

    @Test
    void groupMappedToARoleGrantsThatRole() {
        assertEquals(Set.of(Role.VIEWER), mapping.rolesFor(List.of(VIEWER_GROUP)));
    }

    @Test
    void unmappedGroupGrantsNothing() {
        assertEquals(Set.of(), mapping.rolesFor(List.of("99999999-9999-4999-8999-999999999999")),
                "a group that no role names must grant no role");
    }

    @Test
    void personInSeveralMappedGroupsGetsEveryMappedRole() {
        assertEquals(Set.of(Role.VIEWER, Role.EDITOR), mapping.rolesFor(List.of(VIEWER_GROUP, EDITOR_GROUP)));
    }

    @Test
    void groupIdsMatchIgnoringCase() {
        assertEquals(Set.of(Role.EDITOR), mapping.rolesFor(List.of(EDITOR_GROUP.toUpperCase())),
                "Entra group object ids are GUIDs and compare case-insensitively");
    }

    @Test
    void noGroupsGivesNoRoles() {
        assertEquals(Set.of(), mapping.rolesFor(List.of()));
    }

    @Test
    void configurationListIsSplitIntoTrimmedGroupIds() {
        RoleMapping parsed = RoleMapping.fromConfiguration(
                Map.of(Role.ADMIN, " " + ADMIN_GROUP + " ,  " + VIEWER_GROUP + " "));

        assertEquals(Set.of(Role.ADMIN), parsed.rolesFor(List.of(ADMIN_GROUP)));
        assertEquals(Set.of(Role.ADMIN), parsed.rolesFor(List.of(VIEWER_GROUP)),
                "a group listed under admin grants admin, whatever the spacing");
    }

    @Test
    void emptyConfigurationGrantsNoRole() {
        RoleMapping empty = RoleMapping.fromConfiguration(Map.of(Role.VIEWER, ""));

        assertEquals(Set.of(), empty.rolesFor(List.of(VIEWER_GROUP)),
                "an unset role must not match a group with an empty identifier");
    }
}
