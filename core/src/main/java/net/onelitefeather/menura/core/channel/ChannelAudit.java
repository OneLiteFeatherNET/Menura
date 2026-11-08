package net.onelitefeather.menura.core.channel;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Writes the audit entry of a connection action on the connection of the write it describes. The server implements it
 * with the audit log; system work such as the health check has no person and writes no entry.
 */
public interface ChannelAudit {

    void record(Connection connection, Actor actor, String action, String target) throws SQLException;
}
