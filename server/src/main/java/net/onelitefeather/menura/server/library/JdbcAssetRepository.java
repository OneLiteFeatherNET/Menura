package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.asset.AssetKind;
import net.onelitefeather.menura.core.asset.Checksum;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class JdbcAssetRepository implements AssetRepository {

    private static final String COLUMNS = "id, uploader_id, kind, format, duration_seconds, width, height, size_bytes, "
            + "checksum, stream_title, stream_date, storage_key, created_at";
    private static final String FIND = "select " + COLUMNS + " from menura.asset where id = ?";
    private static final String FIND_DUPLICATE = "select " + COLUMNS + " from menura.asset "
            + "where uploader_id = ? and checksum = ? order by created_at limit 1";
    /** The visibility rule of {@link AssetAccess#mayList}, in SQL: raw material only for its uploader or an admin. */
    private static final String PAGE = "select " + COLUMNS + " from menura.asset "
            + "where (kind <> 'raw_material' or uploader_id = ? or ?) "
            + "and (?::timestamptz is null or (created_at, id) < (?::timestamptz, ?::uuid)) "
            + "order by created_at desc, id desc limit ?";
    private static final String INSERT = "insert into menura.asset (" + COLUMNS
            + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE = "delete from menura.asset where id = ?";

    @Override
    public Optional<AssetRecord> find(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setObject(1, id);
            return readFirst(statement);
        }
    }

    @Override
    public Optional<AssetRecord> findDuplicate(Connection connection, UUID uploaderId, String checksum) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_DUPLICATE)) {
            statement.setObject(1, uploaderId);
            statement.setString(2, checksum);
            return readFirst(statement);
        }
    }

    @Override
    public List<AssetRecord> page(Connection connection, Instant afterCreatedAt, UUID afterId, int limit, UUID viewerId,
                                  boolean seesAllRaw) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PAGE)) {
            statement.setObject(1, viewerId);
            statement.setBoolean(2, seesAllRaw);
            Timestamp after = afterCreatedAt == null ? null : Timestamp.from(afterCreatedAt);
            statement.setTimestamp(3, after);
            statement.setTimestamp(4, after);
            statement.setObject(5, afterId);
            statement.setInt(6, limit);
            List<AssetRecord> rows = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    rows.add(read(row));
                }
            }
            return rows;
        }
    }

    @Override
    public void insert(Connection connection, AssetRecord asset) throws SQLException {
        AssetDeclaration declaration = asset.declaration();
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, asset.id());
            statement.setObject(2, asset.uploaderId());
            statement.setString(3, declaration.kind().value());
            statement.setString(4, declaration.format());
            if (declaration.durationSeconds() == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, declaration.durationSeconds());
            }
            statement.setInt(6, declaration.width());
            statement.setInt(7, declaration.height());
            statement.setLong(8, declaration.sizeBytes());
            statement.setString(9, declaration.checksum().value());
            statement.setString(10, declaration.streamTitle());
            statement.setObject(11, declaration.streamDate() == null ? null : Date.valueOf(declaration.streamDate()));
            statement.setString(12, asset.storageKey());
            statement.setTimestamp(13, Timestamp.from(asset.createdAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private static Optional<AssetRecord> readFirst(PreparedStatement statement) throws SQLException {
        try (ResultSet row = statement.executeQuery()) {
            return row.next() ? Optional.of(read(row)) : Optional.empty();
        }
    }

    private static AssetRecord read(ResultSet row) throws SQLException {
        AssetKind kind = AssetKind.parse(row.getString("kind"));
        Date streamDate = row.getDate("stream_date");
        LocalDate date = streamDate == null ? null : streamDate.toLocalDate();
        int duration = row.getInt("duration_seconds");
        Integer durationSeconds = row.wasNull() ? null : duration;
        AssetDeclaration declaration = new AssetDeclaration(kind, row.getString("format"), durationSeconds,
                row.getInt("width"), row.getInt("height"), row.getLong("size_bytes"),
                Checksum.parse(row.getString("checksum")), row.getString("stream_title"), date);
        return new AssetRecord(row.getObject("id", UUID.class), row.getObject("uploader_id", UUID.class), declaration,
                row.getString("storage_key"), row.getTimestamp("created_at").toInstant());
    }
}
