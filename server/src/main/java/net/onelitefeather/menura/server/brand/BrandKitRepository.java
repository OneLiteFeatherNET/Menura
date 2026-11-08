package net.onelitefeather.menura.server.brand;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Stores the versions of the brand kit and the pins of its consumers. Every call runs on its transaction's connection. */
public interface BrandKitRepository {

    /** The newest published version, if any version exists. */
    Optional<StoredKitVersion> latest(Connection connection) throws SQLException;

    Optional<StoredKitVersion> find(Connection connection, int version) throws SQLException;

    /** Holds a lock for the rest of the transaction, so that version numbers are taken by one publication at a time. */
    void lockPublications(Connection connection) throws SQLException;

    /** Writes a version. Returns false when a version with this number already exists, and writes nothing then. */
    boolean insert(Connection connection, StoredKitVersion version) throws SQLException;

    Optional<KitPin> pin(Connection connection, String consumer) throws SQLException;

    /** Sets the pin of a consumer, creating or replacing it. */
    void savePin(Connection connection, KitPin pin) throws SQLException;

    /** Removes the pin of a consumer. Returns false when the consumer had no pin. */
    boolean deletePin(Connection connection, String consumer) throws SQLException;
}
