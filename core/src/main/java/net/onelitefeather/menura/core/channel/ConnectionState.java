package net.onelitefeather.menura.core.channel;

import java.util.Locale;

/**
 * The state of a channel's connection to its platform. A connected channel may publish; one that needs reconnection
 * keeps its identity and history but is never used for a new publication; a disconnected channel has no grant left.
 */
public enum ConnectionState {
    CONNECTED,
    NEEDS_RECONNECTION,
    DISCONNECTED;

    /** The stored and transmitted name: lower case, with an underscore between words. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a stored name. An unknown name is refused, never guessed. */
    public static ConnectionState parse(String value) {
        for (ConnectionState state : values()) {
            if (state.value().equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown connection state: " + value);
    }
}
