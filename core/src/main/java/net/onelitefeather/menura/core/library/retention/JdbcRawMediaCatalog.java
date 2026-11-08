package net.onelitefeather.menura.core.library.retention;

import net.onelitefeather.menura.core.db.Transactions;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The PostgreSQL catalog of raw media: raw items are the raw-material rows of the asset table (migration V7). */
public final class JdbcRawMediaCatalog implements RawMediaCatalog {

    private static final String SELECT = "select id, storage_key from menura.asset "
            + "where kind = 'raw_material' and media_state = 'available' and created_at < ? order by created_at, id limit ?";
    private static final String UPDATE = "update menura.asset set media_state = ? where id = ? and kind = 'raw_material'";

    private final Transactions transactions;

    public JdbcRawMediaCatalog(Transactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public List<RawMediaRef> olderThan(Instant cutoff, int limit) throws Exception {
        return transactions.inTransaction(connection -> {
            List<RawMediaRef> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
                statement.setTimestamp(1, Timestamp.from(cutoff));
                statement.setInt(2, limit);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        found.add(new RawMediaRef(row.getObject("id", UUID.class), row.getString("storage_key")));
                    }
                }
            }
            return found;
        });
    }

    @Override
    public void setMediaState(UUID rawItem, String state) throws Exception {
        transactions.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPDATE)) {
                statement.setString(1, state);
                statement.setObject(2, rawItem);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
