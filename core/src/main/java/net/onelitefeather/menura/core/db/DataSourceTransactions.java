package net.onelitefeather.menura.core.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs work on one pooled connection in one transaction: commit when the work returns, rollback when it throws. Shared
 * by the server and the jobs; the server registers it as a bean.
 */
public class DataSourceTransactions implements Transactions {

    private final DataSource dataSource;

    public DataSourceTransactions(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T inTransaction(Work<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static void rollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
