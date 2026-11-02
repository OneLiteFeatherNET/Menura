package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps Entra ID group object ids to Menura roles. Every role names the groups that grant it; a person gets every
 * role of every group they belong to, and a group that no role names grants nothing. Group ids are GUIDs and are
 * compared ignoring case.
 */
public final class RoleMapping {

    private final Map<String, Set<Role>> rolesByGroup;

    public RoleMapping(Map<Role, Set<String>> groupsByRole) {
        Objects.requireNonNull(groupsByRole, "groupsByRole");
        Map<String, Set<Role>> index = new HashMap<>();
        groupsByRole.forEach((role, groups) -> {
            for (String group : Objects.requireNonNull(groups, "groups")) {
                index.computeIfAbsent(normalize(group), ignored -> new HashSet<>()).add(role);
            }
        });
        this.rolesByGroup = index.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    /**
     * Builds the mapping from configuration values: for each role, a comma-separated list of group ids as set by an
     * environment variable. Blank entries are ignored, so an unset variable grants nothing.
     */
    public static RoleMapping fromConfiguration(Map<Role, String> commaSeparatedGroupsByRole) {
        Map<Role, Set<String>> groups = new EnumMap<>(Role.class);
        commaSeparatedGroupsByRole.forEach((role, list) -> groups.put(role, parseGroups(list)));
        return new RoleMapping(groups);
    }

    /** Splits a comma-separated list of group ids, trimming each and dropping blank entries. */
    static Set<String> parseGroups(String commaSeparated) {
        if (commaSeparated == null) {
            return Set.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The roles granted by the given group ids. Empty when none of them is mapped. */
    public Set<Role> rolesFor(Collection<String> groups) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String group : groups) {
            roles.addAll(rolesByGroup.getOrDefault(normalize(group), Set.of()));
        }
        return Set.copyOf(roles);
    }

    private static String normalize(String group) {
        return group.trim().toLowerCase(Locale.ROOT);
    }
}
