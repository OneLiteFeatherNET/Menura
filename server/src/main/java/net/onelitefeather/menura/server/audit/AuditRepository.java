package net.onelitefeather.menura.server.audit;

import java.sql.Connection;
import java.sql.SQLException;

/** Stores audit entries. Every insert runs on the connection of the write it describes. */
public interface AuditRepository {

    void insert(Connection connection, AuditEntry entry) throws SQLException;
}
