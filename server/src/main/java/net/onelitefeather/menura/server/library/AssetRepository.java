package net.onelitefeather.menura.server.library;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores verified assets. Every call runs on its transaction's connection. */
public interface AssetRepository {

    Optional<AssetRecord> find(Connection connection, UUID id) throws SQLException;

    /** An asset of the uploader with this checksum, the duplicate the editor is asked about. */
    Optional<AssetRecord> findDuplicate(Connection connection, UUID uploaderId, String checksum) throws SQLException;

    /**
     * One page of the assets the viewer may see, newest first, after the position of {@code after} (null for the
     * first page). The rule is {@link AssetAccess#mayList}; the SQL implementation applies the same rule in its query.
     */
    List<AssetRecord> page(Connection connection, Instant afterCreatedAt, UUID afterId, int limit, UUID viewerId,
                           boolean seesAllRaw) throws SQLException;

    void insert(Connection connection, AssetRecord asset) throws SQLException;

    void delete(Connection connection, UUID id) throws SQLException;
}
