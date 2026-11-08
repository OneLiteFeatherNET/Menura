package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The in-memory pool of the prototype, seeded from {@link PoolSeed} at construction. Reads share the read lock, saves
 * take the write lock. {@link #all()} keeps the order of the store (seed order, then saves); sorting is left to the
 * caller. At most {@link #CAPACITY} assets are held: a save of a new asset beyond that throws
 * {@link AssetCapacityExceededException}, a save of an existing id always replaces it.
 */
@Singleton
@Requires(env = "prototype")
public class AssetRepository {

    public static final int CAPACITY = 500;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final Map<String, List<TranscriptSegment>> transcripts = new HashMap<>();
    private final Map<String, Person> people = new LinkedHashMap<>();
    /** The person each signed-in subject is mapped to. Guarded by the same lock as the other maps. */
    private final Map<String, String> personBySubject = new HashMap<>();
    /** The number of the next simulated upload; the ids of new assets continue after the seed. */
    private int nextAssetNumber = 1000;

    public AssetRepository() {
        for (Person person : PoolSeed.people()) {
            people.put(person.id(), person);
        }
        for (Asset asset : PoolSeed.assets()) {
            assets.put(asset.id(), asset);
        }
        transcripts.putAll(PoolSeed.transcripts());
    }

    /** A snapshot of all assets in store order. The list cannot change the store. */
    public List<Asset> all() {
        lock.readLock().lock();
        try {
            return List.copyOf(assets.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Asset> find(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(assets.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Stores the asset under its id and returns it.
     *
     * @throws AssetCapacityExceededException when the id is new and the store already holds {@link #CAPACITY} assets
     */
    public Asset save(Asset asset) {
        lock.writeLock().lock();
        try {
            if (!assets.containsKey(asset.id()) && assets.size() >= CAPACITY) {
                throw new AssetCapacityExceededException();
            }
            assets.put(asset.id(), asset);
            return asset;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** The timed segments of an asset; empty unless its transcript is ready, and for unknown assets. */
    public List<TranscriptSegment> transcript(String assetId) {
        lock.readLock().lock();
        try {
            return transcripts.getOrDefault(assetId, List.of());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Person> people() {
        lock.readLock().lock();
        try {
            return List.copyOf(people.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Person> person(String id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(people.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** The person a signed-in subject is mapped to, or empty when the subject has not written anything yet. */
    public Optional<String> personIdOf(String subject) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(personBySubject.get(subject));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the person of a subject, creating it with {@code name} and {@code newPersonId} on the first write of
     * that subject. An existing mapping is never changed.
     */
    public Person ensurePersonOf(String subject, String newPersonId, String name) {
        lock.writeLock().lock();
        try {
            String existing = personBySubject.get(subject);
            if (existing != null) {
                return people.get(existing);
            }
            Person created = new Person(newPersonId, name);
            people.put(created.id(), created);
            personBySubject.put(subject, created.id());
            return created;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** A new asset id, unique within this repository. */
    public String nextAssetId() {
        lock.writeLock().lock();
        try {
            return "a-" + nextAssetNumber++;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
