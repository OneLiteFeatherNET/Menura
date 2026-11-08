package net.onelitefeather.menura.server.brand;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

/**
 * Checks which library assets a brand kit references. Only verified assets are rows of the library table, so a row is
 * the proof that an upload completed. The kit refers to fonts and logos by id; no media bytes pass through here.
 */
public interface BrandKitAssets {

    /** The referenced ids that are not verified assets of the library. Empty when every reference resolves. */
    Set<UUID> unknown(Connection connection, Set<UUID> referenced) throws SQLException;
}
