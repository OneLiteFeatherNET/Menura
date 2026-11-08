package net.onelitefeather.menura.server.brand;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The verified library assets of a test: a test verifies the ids it wants to reference. */
@Singleton
@Replaces(JdbcBrandKitAssets.class)
public class InMemoryBrandKitAssets implements BrandKitAssets {

    private final Set<UUID> verified = new HashSet<>();

    @Override
    public Set<UUID> unknown(Connection connection, Set<UUID> referenced) {
        Set<UUID> unknown = new HashSet<>(referenced);
        unknown.removeAll(verified);
        return Set.copyOf(unknown);
    }

    public void verify(UUID id) {
        verified.add(id);
    }

    /** Forgets every verified asset, so that each test starts with none. */
    public void clear() {
        verified.clear();
    }
}
