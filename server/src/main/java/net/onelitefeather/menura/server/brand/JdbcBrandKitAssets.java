package net.onelitefeather.menura.server.brand;

import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Looks each referenced id up in the library's asset table, on the publication's own connection. */
@Singleton
public class JdbcBrandKitAssets implements BrandKitAssets {

    private static final String FIND = "select id from menura.asset where id = ?";

    @Override
    public Set<UUID> unknown(Connection connection, Set<UUID> referenced) throws SQLException {
        Set<UUID> unknown = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(FIND)) {
            for (UUID id : referenced) {
                statement.setObject(1, id);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        unknown.add(id);
                    }
                }
            }
        }
        return Set.copyOf(unknown);
    }
}
