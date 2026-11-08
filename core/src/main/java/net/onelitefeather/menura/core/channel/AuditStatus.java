package net.onelitefeather.menura.core.channel;

import java.util.List;
import java.util.Locale;

/**
 * The audit status of a platform app, recorded by an administrator. An app that was never recorded is unknown, and an
 * unknown status is treated like an unaudited one: no restriction is assumed to be lifted.
 */
public enum AuditStatus {
    UNKNOWN,
    UNAUDITED,
    AUDITED;

    /** The restriction every app without a passed audit carries. */
    public static final String PUBLIC_VISIBILITY_RESTRICTION = "public visibility not available";

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AuditStatus parse(String value) {
        for (AuditStatus status : values()) {
            if (status.value().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown audit status: " + value);
    }

    /** The text shown on the channel view for this status. */
    public String displayText() {
        return switch (this) {
            case UNKNOWN -> "unknown";
            case UNAUDITED -> "unaudited: uploads private only";
            case AUDITED -> "audited";
        };
    }

    /** The restrictions this status implies for publications. */
    public List<String> restrictions() {
        return this == AUDITED ? List.of() : List.of(PUBLIC_VISIBILITY_RESTRICTION);
    }
}
