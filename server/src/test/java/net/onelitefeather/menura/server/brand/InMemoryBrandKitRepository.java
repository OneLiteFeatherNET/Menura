package net.onelitefeather.menura.server.brand;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Keeps brand kit versions and pins in memory, with the same no-overwrite rule as the SQL insert. */
@Singleton
@Replaces(JdbcBrandKitRepository.class)
public class InMemoryBrandKitRepository implements BrandKitRepository {

    private final Map<Integer, StoredKitVersion> versions = new LinkedHashMap<>();
    private final Map<String, KitPin> pins = new LinkedHashMap<>();

    @Override
    public Optional<StoredKitVersion> latest(Connection connection) {
        return versions.values().stream().max(Comparator.comparingInt(StoredKitVersion::version));
    }

    @Override
    public Optional<StoredKitVersion> find(Connection connection, int version) {
        return Optional.ofNullable(versions.get(version));
    }

    @Override
    public void lockPublications(Connection connection) {
        // A single in-memory writer per test: nothing to lock.
    }

    @Override
    public boolean insert(Connection connection, StoredKitVersion version) {
        return versions.putIfAbsent(version.version(), version) == null;
    }

    @Override
    public Optional<KitPin> pin(Connection connection, String consumer) {
        return Optional.ofNullable(pins.get(consumer));
    }

    @Override
    public void savePin(Connection connection, KitPin pin) {
        pins.put(pin.consumer(), pin);
    }

    @Override
    public boolean deletePin(Connection connection, String consumer) {
        return pins.remove(consumer) != null;
    }

    /** Forgets every version and pin, so that each test starts from an empty kit. */
    public void clear() {
        versions.clear();
        pins.clear();
    }
}
