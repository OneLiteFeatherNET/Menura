package net.onelitefeather.menura.server.library;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.asset.AssetKind;
import net.onelitefeather.menura.core.asset.Checksum;
import net.onelitefeather.menura.core.asset.PartPlan;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class JdbcUploadSessionRepository implements UploadSessionRepository {

    private static final String COLUMNS = "id, uploader_id, kind, format, duration_seconds, width, height, size_bytes, "
            + "checksum, part_size_bytes, part_count, stream_title, stream_date, state, duplicate_of, storage_key, "
            + "storage_upload_id, failure_reason, asset_id, created_at, updated_at";
    private static final String FIND = "select " + COLUMNS + " from menura.upload_session where id = ?";
    private static final String FIND_FOR_UPDATE = FIND + " for update";
    private static final String INSERT = "insert into menura.upload_session (" + COLUMNS
            + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "update menura.upload_session set state = ?, storage_upload_id = ?, "
            + "failure_reason = ?, asset_id = ?, updated_at = ? where id = ?";
    private static final String UPSERT_PART = "insert into menura.upload_session_part "
            + "(session_id, part_number, size_bytes, etag, checksum_sha256) values (?, ?, ?, ?, ?) "
            + "on conflict (session_id, part_number) do update set size_bytes = excluded.size_bytes, "
            + "etag = excluded.etag, checksum_sha256 = excluded.checksum_sha256";

    @Override
    public Optional<UploadSession> find(Connection connection, UUID id) throws SQLException {
        return findWith(connection, FIND, id);
    }

    @Override
    public Optional<UploadSession> findForUpdate(Connection connection, UUID id) throws SQLException {
        return findWith(connection, FIND_FOR_UPDATE, id);
    }

    private Optional<UploadSession> findWith(Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        }
    }

    @Override
    public void insert(Connection connection, UploadSession session) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            AssetDeclaration declaration = session.declaration();
            statement.setObject(1, session.id());
            statement.setObject(2, session.uploaderId());
            statement.setString(3, declaration.kind().value());
            statement.setString(4, declaration.format());
            setInteger(statement, 5, declaration.durationSeconds());
            statement.setInt(6, declaration.width());
            statement.setInt(7, declaration.height());
            statement.setLong(8, declaration.sizeBytes());
            statement.setString(9, declaration.checksum().value());
            statement.setLong(10, session.plan().partSizeBytes());
            statement.setInt(11, session.plan().partCount());
            statement.setString(12, declaration.streamTitle());
            statement.setObject(13, declaration.streamDate() == null ? null : Date.valueOf(declaration.streamDate()));
            statement.setString(14, session.state().value());
            statement.setObject(15, session.duplicateOf());
            statement.setString(16, session.storageKey());
            statement.setString(17, session.storageUploadId());
            statement.setString(18, session.failureReason());
            statement.setObject(19, session.assetId());
            statement.setTimestamp(20, Timestamp.from(session.createdAt()));
            statement.setTimestamp(21, Timestamp.from(session.updatedAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public void update(Connection connection, UploadSession session) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, session.state().value());
            statement.setString(2, session.storageUploadId());
            statement.setString(3, session.failureReason());
            statement.setObject(4, session.assetId());
            statement.setTimestamp(5, Timestamp.from(session.updatedAt()));
            statement.setObject(6, session.id());
            statement.executeUpdate();
        }
    }

    @Override
    public void recordParts(Connection connection, UUID sessionId, Collection<ObjectStorage.StoredPart> parts)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_PART)) {
            for (ObjectStorage.StoredPart part : parts) {
                statement.setObject(1, sessionId);
                statement.setInt(2, part.partNumber());
                statement.setLong(3, part.sizeBytes());
                statement.setString(4, part.etag());
                statement.setString(5, part.checksumSha256());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static UploadSession read(ResultSet row) throws SQLException {
        AssetKind kind = AssetKind.parse(row.getString("kind"));
        LocalDate streamDate = row.getDate("stream_date") == null ? null : row.getDate("stream_date").toLocalDate();
        AssetDeclaration declaration = new AssetDeclaration(kind, row.getString("format"),
                nullableInt(row, "duration_seconds"), row.getInt("width"), row.getInt("height"),
                row.getLong("size_bytes"), Checksum.parse(row.getString("checksum")),
                row.getString("stream_title"), streamDate);
        PartPlan plan = new PartPlan(row.getLong("size_bytes"), row.getLong("part_size_bytes"), row.getInt("part_count"));
        return new UploadSession(row.getObject("id", UUID.class), row.getObject("uploader_id", UUID.class), declaration,
                plan, UploadState.parse(row.getString("state")), row.getObject("duplicate_of", UUID.class),
                row.getString("storage_key"), row.getString("storage_upload_id"), row.getString("failure_reason"),
                row.getObject("asset_id", UUID.class), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static Integer nullableInt(ResultSet row, String column) throws SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }
}
