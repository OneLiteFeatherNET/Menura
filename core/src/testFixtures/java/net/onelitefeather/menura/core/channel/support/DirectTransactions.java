package net.onelitefeather.menura.core.channel.support;

import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.db.Work;

/** Runs the work without a database; the in-memory store ignores the connection argument. */
public final class DirectTransactions implements Transactions {

    @Override
    public <T> T inTransaction(Work<T> work) throws java.sql.SQLException {
        return work.apply(null);
    }
}
