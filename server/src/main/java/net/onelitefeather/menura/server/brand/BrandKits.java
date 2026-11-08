package net.onelitefeather.menura.server.brand;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.core.brand.BrandKitRules;
import net.onelitefeather.menura.core.brand.InitialBrandKit;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.AuditRepository;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.security.Caller;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The central brand kit: publication of versions, reads of a version (the newest, a named one, or the one a consumer has
 * pinned), and the pins of consumers. Versions are immutable; a refused publication writes nothing. The first version is
 * seeded from the overlay theme the first time the kit is touched, by the system rather than by a person.
 */
@Singleton
public class BrandKits {

    /** The person named on the seeded first version and on its audit entry. */
    static final UUID SYSTEM_PERSON = new UUID(0L, 0L);
    private static final String TARGET = "brand_kit";
    private static final Pattern CONSUMER = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final Transactions transactions;
    private final AuditLog auditLog;
    private final AuditRepository audit;
    private final BrandKitRepository kits;
    private final IdentifierGenerator identifiers;
    private final Clock clock;

    public BrandKits(Transactions transactions, AuditLog auditLog, AuditRepository audit, BrandKitRepository kits,
                     IdentifierGenerator identifiers, Clock clock) {
        this.transactions = transactions;
        this.auditLog = auditLog;
        this.audit = audit;
        this.kits = kits;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    /**
     * Publishes a draft as the next version. The draft is checked first; a refusal names the violations and writes
     * nothing. The version and its audit entry commit together.
     */
    public StoredKitVersion publish(CurrentPerson viewer, BrandKit draft, String requestId) throws SQLException {
        BrandKit prepared = BrandKitRules.prepare(draft);
        Caller caller = new Caller(viewer.personId(), viewer.channel());
        return auditLog.write(caller, "brand_kit.publish", TARGET, requestId, connection -> {
            kits.lockPublications(connection);
            seedIfEmpty(connection);
            int next = kits.latest(connection).map(latest -> latest.version() + 1).orElse(1);
            StoredKitVersion version = new StoredKitVersion(next, prepared, viewer.personId(), clock.instant());
            if (!kits.insert(connection, version)) {
                throw new ProblemException(409, "Publication Conflict", "Another publication took the version number; retry.");
            }
            return version;
        });
    }

    /** The newest version. */
    public StoredKitVersion current() throws SQLException {
        return transactions.inTransaction(connection -> {
            seedIfEmpty(connection);
            return kits.latest(connection).orElseThrow(() -> new IllegalStateException("the seeded kit is missing"));
        });
    }

    /**
     * The version a read names: the given version, else the version the consumer has pinned, else the newest. A consumer
     * without a pin follows the newest version. A named version that does not exist is 404.
     */
    public StoredKitVersion resolve(Integer version, String consumer) throws SQLException {
        if (consumer != null) {
            requireConsumer(consumer);
        }
        return transactions.inTransaction(connection -> {
            seedIfEmpty(connection);
            if (version != null) {
                return version(connection, version);
            }
            if (consumer != null) {
                var pin = kits.pin(connection, consumer);
                if (pin.isPresent()) {
                    return version(connection, pin.get().version());
                }
            }
            return kits.latest(connection).orElseThrow(() -> new IllegalStateException("the seeded kit is missing"));
        });
    }

    /** The pin of a consumer, or 404 when it has none. */
    public KitPin pinOf(String consumer) throws SQLException {
        requireConsumer(consumer);
        return transactions.inTransaction(connection -> {
            seedIfEmpty(connection);
            return kits.pin(connection, consumer)
                    .orElseThrow(() -> new ProblemException(404, "Not Found", "The consumer has no pinned brand kit version."));
        });
    }

    /** Pins a consumer to an existing version. The version must exist; the pin and its audit entry commit together. */
    public KitPin pin(CurrentPerson viewer, String consumer, int version, String requestId) throws SQLException {
        requireConsumer(consumer);
        Caller caller = new Caller(viewer.personId(), viewer.channel());
        return auditLog.write(caller, "brand_kit.pin", "brand_kit_consumer:" + consumer, requestId, connection -> {
            seedIfEmpty(connection);
            version(connection, version);
            KitPin pin = new KitPin(consumer, version, clock.instant());
            kits.savePin(connection, pin);
            return pin;
        });
    }

    /** Removes the pin of a consumer, so that it follows the newest version again. 404 when it had no pin. */
    public void unpin(CurrentPerson viewer, String consumer, String requestId) throws SQLException {
        requireConsumer(consumer);
        Caller caller = new Caller(viewer.personId(), viewer.channel());
        auditLog.write(caller, "brand_kit.unpin", "brand_kit_consumer:" + consumer, requestId, connection -> {
            if (!kits.deletePin(connection, consumer)) {
                throw new ProblemException(404, "Not Found", "The consumer has no pinned brand kit version.");
            }
            return null;
        });
    }

    /** Seeds the first version when no version exists. Idempotent: a second call writes nothing. */
    void seedIfEmpty(Connection connection) throws SQLException {
        if (kits.latest(connection).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        StoredKitVersion seed = new StoredKitVersion(1, BrandKitRules.prepare(InitialBrandKit.create()), SYSTEM_PERSON, now);
        if (kits.insert(connection, seed)) {
            audit.insert(connection, new AuditEntry(identifiers.next(), SYSTEM_PERSON, "brand_kit.seed", TARGET, now,
                    Channel.WEB, "seed"));
        }
    }

    private StoredKitVersion version(Connection connection, int version) throws SQLException {
        return kits.find(connection, version)
                .orElseThrow(() -> new ProblemException(404, "Not Found", "No brand kit version with this number."));
    }

    /** A consumer name is lower case, starts with a letter and is at most 64 characters long. */
    static void requireConsumer(String consumer) {
        if (!CONSUMER.matcher(consumer).matches()) {
            throw new ProblemException(400, "Invalid consumer",
                    "A consumer is named by 1 to 64 lower case letters, digits and hyphens, starting with a letter.");
        }
    }
}
