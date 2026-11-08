package net.onelitefeather.menura.core.library;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Locale;

/** Whether a clip is an active part of the pool or a highlight candidate that a person or an MCP client suggested. */
public enum ClipStatus {
    ACTIVE("active"),
    SUGGESTED("suggested");

    private final String value;

    ClipStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Parses a status named by a client; an unknown name is refused as a problem with the field {@code status}. */
    public static ClipStatus parse(String name) {
        if (name == null) {
            return ACTIVE;
        }
        for (ClipStatus status : values()) {
            if (status.value.equals(name.trim().toLowerCase(Locale.ROOT))) {
                return status;
            }
        }
        throw new ProblemException(400, "Invalid status", "The field 'status' must be active or suggested.");
    }
}
