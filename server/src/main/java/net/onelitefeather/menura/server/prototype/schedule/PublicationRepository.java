package net.onelitefeather.menura.server.prototype.schedule;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.prototype.DemoStore;
import net.onelitefeather.menura.server.prototype.pool.AssetRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * The in-memory publications of the prototype, seeded from {@link ScheduleSeed}. Every method holds one lock, and
 * {@link #locked} lets a caller keep a check and its write under the same lock. At most {@link #CAPACITY} publications
 * are held; {@link #insertAll} refuses a batch that would exceed it. Only planned, scheduled, uploading and published
 * publications block a clip on a channel; failed ones do not.
 */
@Singleton
@Requires(env = "prototype")
public class PublicationRepository {

    public static final int CAPACITY = 1000;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Publication> publications = new LinkedHashMap<>();
    private int lastNumber;

    public PublicationRepository(Clock clock, AssetRepository assets, DemoStore store) {
        for (Publication seeded : ScheduleSeed.publications(clock.instant(), assets, store)) {
            publications.put(seeded.id(), seeded);
            lastNumber = Math.max(lastNumber, numberOf(seeded.id()));
        }
    }

    /** Runs the work while no other caller of this repository runs. The lock is reentrant. */
    public <T> T locked(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    public List<Publication> all() {
        return locked(() -> List.copyOf(publications.values()));
    }

    public Optional<Publication> find(String id) {
        return locked(() -> Optional.ofNullable(publications.get(id)));
    }

    public int size() {
        return locked(publications::size);
    }

    /** A fresh id that no stored publication has. */
    public String nextId() {
        return locked(() -> {
            lastNumber++;
            return "pub-" + lastNumber;
        });
    }

    /**
     * Stores the publications as one batch.
     *
     * @throws ProblemException 422 when the batch would exceed {@link #CAPACITY}; nothing is stored then
     */
    public void insertAll(List<Publication> batch) {
        locked(() -> {
            if (publications.size() + batch.size() > CAPACITY) {
                throw new ProblemException(422, "Unprocessable Content",
                        "Es sind höchstens " + CAPACITY + " Veröffentlichungen erlaubt.");
            }
            batch.forEach(publication -> publications.put(publication.id(), publication));
            return null;
        });
    }

    /** Replaces the stored publication with the same id. */
    public void replace(Publication publication) {
        locked(() -> publications.put(publication.id(), publication));
    }

    public void remove(String id) {
        locked(() -> publications.remove(id));
    }

    /** True when the clip has a publication on the channel that is not failed. */
    public boolean hasActive(String assetId, String channelId) {
        return locked(() -> publications.values().stream().anyMatch(p -> p.status() != Publication.Status.failed
                && assetId.equals(p.assetId()) && channelId.equals(p.channelId())));
    }

    /** True when the clip has any publication that is not failed, on any channel. */
    public boolean hasActive(String assetId) {
        return locked(() -> publications.values().stream().anyMatch(p -> p.status() != Publication.Status.failed
                && assetId.equals(p.assetId())));
    }

    /** True when the channel has a publication that is not failed at exactly this instant. */
    public boolean occupied(String channelId, Instant at) {
        return locked(() -> publications.values().stream().anyMatch(p -> p.status() != Publication.Status.failed
                && channelId.equals(p.channelId()) && at.equals(p.publishAt())));
    }

    private static int numberOf(String id) {
        try {
            return Integer.parseInt(id.substring("pub-".length()));
        } catch (NumberFormatException | IndexOutOfBoundsException notANumber) {
            return 0;
        }
    }
}
