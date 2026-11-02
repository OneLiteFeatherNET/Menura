package net.onelitefeather.menura.core.db;

import java.sql.Connection;
import java.sql.SQLException;

/** A unit of database work that runs on one connection, inside one transaction. */
@FunctionalInterface
public interface Work<T> {

    T apply(Connection connection) throws SQLException;
}
