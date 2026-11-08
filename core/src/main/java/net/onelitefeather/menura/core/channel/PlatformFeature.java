package net.onelitefeather.menura.core.channel;

import java.util.Objects;
import java.util.Set;

/** A platform feature a channel can be enabled for, with the scopes that feature needs at the platform. */
public record PlatformFeature(String name, Set<String> scopes) {

    public PlatformFeature {
        Objects.requireNonNull(name, "name");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
    }
}
