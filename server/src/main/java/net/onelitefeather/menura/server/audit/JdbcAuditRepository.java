package net.onelitefeather.menura.server.audit;

import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

@Singleton
public class JdbcAuditRepository implements AuditRepository {

    private static final String INSERT = "insert into menura.audit_log "
            + "(id, person_id, action, target, occurred_at, channel, request_id) values (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public void insert(Connection connection, AuditEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, entry.id());
            statement.setObject(2, entry.personId());
            statement.setString(3, entry.action());
            statement.setString(4, entry.target());
            statement.setTimestamp(5, Timestamp.from(entry.occurredAt()));
            statement.setString(6, entry.channel().value());
            statement.setString(7, entry.requestId());
            statement.executeUpdate();
        }
    }
}
