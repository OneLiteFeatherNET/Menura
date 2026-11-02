package net.onelitefeather.menura.core.db;

import java.sql.SQLException;

/**
 * Runs database work in one transaction. The work commits as a whole or, when it throws, rolls back as a whole.
 * Writes that must leave a trace (the audit log) run in the same transaction as the write they describe.
 */
public interface Transactions {

    <T> T inTransaction(Work<T> work) throws SQLException;
}
