package net.onelitefeather.menura.core.auth;

import java.util.Locale;
import java.util.Optional;

/**
 * The five roles a person can hold. A role grants only its own rights: there is no implied hierarchy, so an admin
 * passes an endpoint only when that endpoint names admin.
 */
public enum Role {
    VIEWER,
    EDITOR,
    PUBLISHER,
    OPERATOR,
    ADMIN;

    /** Parses a role name case-insensitively. An unknown name yields empty, which grants nothing. */
    public static Optional<Role> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
