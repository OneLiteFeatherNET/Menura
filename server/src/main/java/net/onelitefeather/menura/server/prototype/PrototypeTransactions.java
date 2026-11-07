package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.db.Work;
import net.onelitefeather.menura.server.db.DataSourceTransactions;

import java.sql.SQLException;

/**
 * Transactions of the prototype, without a database. The in-memory repositories ignore the connection they are
 * given, so the work runs with none; a write and its audit entry still happen together, because the in-memory
 * repositories are called inside the same {@code apply}. Active only in the prototype environment.
 */
@Singleton
@Replaces(DataSourceTransactions.class)
@Requires(env = "prototype")
public class PrototypeTransactions implements Transactions {

    @Override
    public <T> T inTransaction(Work<T> work) throws SQLException {
        return work.apply(null);
    }
}
