package net.onelitefeather.menura.server.brand;

import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.brand.BrandKit;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** The brand kit tables of PostgreSQL. A version is stored as one JSON document; the document is never edited. */
@Singleton
public class JdbcBrandKitRepository implements BrandKitRepository {

    private static final String VERSION_COLUMNS = "version, document::text, published_by, created_at";
    private static final String LATEST = "select " + VERSION_COLUMNS
            + " from menura.brand_kit_version order by version desc limit 1";
    private static final String FIND = "select " + VERSION_COLUMNS + " from menura.brand_kit_version where version = ?";
    private static final String LOCK = "select pg_advisory_xact_lock(hashtext('menura.brand_kit'))";
    private static final String INSERT = "insert into menura.brand_kit_version (version, document, published_by, created_at) "
            + "values (?, ?::jsonb, ?, ?) on conflict (version) do nothing";
    private static final String PIN = "select consumer, version, updated_at from menura.brand_kit_pin where consumer = ?";
    private static final String SAVE_PIN = "insert into menura.brand_kit_pin (consumer, version, updated_at) values (?, ?, ?) "
            + "on conflict (consumer) do update set version = excluded.version, updated_at = excluded.updated_at";
    private static final String DELETE_PIN = "delete from menura.brand_kit_pin where consumer = ?";

    private final ObjectMapper json;

    public JdbcBrandKitRepository(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Optional<StoredKitVersion> latest(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LATEST);
             ResultSet row = statement.executeQuery()) {
            return row.next() ? Optional.of(readVersion(row)) : Optional.empty();
        }
    }

    @Override
    public Optional<StoredKitVersion> find(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setInt(1, version);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readVersion(row)) : Optional.empty();
            }
        }
    }

    @Override
    public void lockPublications(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK);
             ResultSet row = statement.executeQuery()) {
            row.next();
        }
    }

    @Override
    public boolean insert(Connection connection, StoredKitVersion version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setInt(1, version.version());
            statement.setString(2, toJson(version.kit()));
            statement.setObject(3, version.publishedBy());
            statement.setTimestamp(4, Timestamp.from(version.createdAt()));
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<KitPin> pin(Connection connection, String consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PIN)) {
            statement.setString(1, consumer);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new KitPin(row.getString("consumer"), row.getInt("version"),
                        row.getTimestamp("updated_at").toInstant()));
            }
        }
    }

    @Override
    public void savePin(Connection connection, KitPin pin) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SAVE_PIN)) {
            statement.setString(1, pin.consumer());
            statement.setInt(2, pin.version());
            statement.setTimestamp(3, Timestamp.from(pin.updatedAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public boolean deletePin(Connection connection, String consumer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_PIN)) {
            statement.setString(1, consumer);
            return statement.executeUpdate() == 1;
        }
    }

    private StoredKitVersion readVersion(ResultSet row) throws SQLException {
        return new StoredKitVersion(row.getInt("version"), fromJson(row.getString("document")),
                row.getObject("published_by", UUID.class), row.getTimestamp("created_at").toInstant());
    }

    private String toJson(BrandKit kit) {
        try {
            return json.writeValueAsString(kit);
        } catch (IOException failure) {
            throw new IllegalStateException("the brand kit could not be serialised", failure);
        }
    }

    private BrandKit fromJson(String document) throws SQLException {
        try {
            return json.readValue(document, BrandKit.class);
        } catch (IOException failure) {
            throw new SQLException("a stored brand kit version is unreadable", failure);
        }
    }
}
