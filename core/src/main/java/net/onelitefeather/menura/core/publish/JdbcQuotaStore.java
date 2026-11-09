package net.onelitefeather.menura.core.publish;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/** PostgreSQL implementation of the quota usage store over the table {@code menura.youtube_quota_usage} (migration V14). */
public final class JdbcQuotaStore implements QuotaLedger.Store {

    @Override
    public QuotaLedger.Usage usage(Connection connection, UUID channelId, LocalDate day) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select units, uploads from menura.youtube_quota_usage "
                + "where channel_id = ? and quota_day = ?")) {
            statement.setObject(1, channelId);
            statement.setObject(2, day);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? new QuotaLedger.Usage(rs.getInt(1), rs.getInt(2)) : new QuotaLedger.Usage(0, 0);
            }
        }
    }

    @Override
    public void save(Connection connection, UUID channelId, LocalDate day, QuotaLedger.Usage usage) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.youtube_quota_usage "
                + "(channel_id, quota_day, units, uploads) values (?, ?, ?, ?) on conflict (channel_id, quota_day) "
                + "do update set units = excluded.units, uploads = excluded.uploads")) {
            statement.setObject(1, channelId);
            statement.setObject(2, day);
            statement.setInt(3, usage.units());
            statement.setInt(4, usage.uploads());
            statement.executeUpdate();
        }
    }
}
