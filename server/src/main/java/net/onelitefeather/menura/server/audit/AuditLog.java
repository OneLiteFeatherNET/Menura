package net.onelitefeather.menura.server.audit;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.db.Work;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.security.Caller;

import java.sql.SQLException;
import java.time.Clock;

/**
 * The audit log of every write. A write goes through {@link #write}, which runs the write and its audit entry in
 * one transaction: the entry commits with the write, and a write that fails or rolls back leaves no entry.
 */
@Singleton
public class AuditLog {

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final AuditRepository repository;

    public AuditLog(Transactions transactions, Clock clock, IdentifierGenerator identifiers, AuditRepository repository) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.repository = repository;
    }

    /**
     * Runs {@code write} and records one audit entry for it, in the same transaction. Returns what the write returns.
     *
     * @param action    what was done, for example {@code calendar_feed_token.issue}
     * @param target    what it was done to, for example {@code person:<id>}
     * @param requestId the request that caused the write
     */
    public <T> T write(Caller caller, String action, String target, String requestId, Work<T> write) throws SQLException {
        return transactions.inTransaction(connection -> {
            T result = write.apply(connection);
            repository.insert(connection, new AuditEntry(identifiers.next(), caller.personId(), action, target,
                    clock.instant(), caller.channel(), requestId));
            return result;
        });
    }
}
