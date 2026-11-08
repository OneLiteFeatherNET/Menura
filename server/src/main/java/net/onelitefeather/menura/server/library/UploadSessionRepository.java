package net.onelitefeather.menura.server.library;


import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Stores upload sessions and the parts the storage confirmed for them. Every call runs on its transaction's connection. */
public interface UploadSessionRepository {

    Optional<UploadSession> find(Connection connection, UUID id) throws SQLException;

    /** Like {@link #find} but locks the row until the transaction ends, so a state change is checked and written atomically. */
    Optional<UploadSession> findForUpdate(Connection connection, UUID id) throws SQLException;

    void insert(Connection connection, UploadSession session) throws SQLException;

    void update(Connection connection, UploadSession session) throws SQLException;

    /** Records the parts the storage reports, replacing earlier records of the same part numbers. */
    void recordParts(Connection connection, UUID sessionId, Collection<ObjectStorage.StoredPart> parts)
            throws SQLException;
}
