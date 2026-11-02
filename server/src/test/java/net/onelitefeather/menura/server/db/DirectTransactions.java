package net.onelitefeather.menura.server.db;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.db.Work;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs work on a placeholder connection without a database. Each transaction gets its own connection object, so a
 * test can check which transaction a write or an audit entry belongs to. Each test context gets its own instance.
 */
@Singleton
@Replaces(DataSourceTransactions.class)
public class DirectTransactions implements Transactions {

    private final List<Connection> connections = new ArrayList<>();

    @Override
    public <T> T inTransaction(Work<T> work) throws SQLException {
        Connection connection = placeholderConnection();
        connections.add(connection);
        return work.apply(connection);
    }

    /** The connection of every transaction started so far, in order. */
    public List<Connection> connections() {
        return List.copyOf(connections);
    }

    private static Connection placeholderConnection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> null);
    }
}
