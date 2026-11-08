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
     * One page of the shared pool, newest first, after the position of {@code afterCreatedAt} and {@code afterId} (both
     * null for the first page). The table holds only verified assets, so every row is listed; who may read the pool is
     * decided by {@link AssetAccess#mayRead} before this is called.
     */
    List<AssetRecord> page(Connection connection, Instant afterCreatedAt, UUID afterId, int limit) throws SQLException;

    void insert(Connection connection, AssetRecord asset) throws SQLException;

    void delete(Connection connection, UUID id) throws SQLException;
}
