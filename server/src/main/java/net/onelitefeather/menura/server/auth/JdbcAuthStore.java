package net.onelitefeather.menura.server.auth;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.auth.Role;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** PostgreSQL implementation of {@link AuthStore}, over the tables of migration V2 (person, auth_login, auth_session). */
@Singleton
public class JdbcAuthStore implements AuthStore {

    private final DataSource dataSource;

    public JdbcAuthStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveSignIn(SignIn signIn) {
        execute("INSERT INTO auth_login (state_hash, nonce, code_verifier, expires_at) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setBytes(1, signIn.stateHash());
                    statement.setString(2, signIn.nonce());
                    statement.setString(3, signIn.codeVerifier());
                    statement.setTimestamp(4, Timestamp.from(signIn.expiresAt()));
                });
    }

    @Override
    public Optional<SignIn> takeSignIn(byte[] stateHash, Instant now) {
        return query("DELETE FROM auth_login WHERE state_hash = ? RETURNING nonce, code_verifier, expires_at",
                statement -> statement.setBytes(1, stateHash), result -> {
                    if (!result.next()) {
                        return Optional.<SignIn>empty();
                    }
                    Instant expiresAt = result.getTimestamp("expires_at").toInstant();
                    if (!expiresAt.isAfter(now)) {
                        return Optional.<SignIn>empty();
                    }
                    return Optional.of(new SignIn(stateHash, result.getString("nonce"),
                            result.getString("code_verifier"), expiresAt));
                });
    }

    /** One statement: the insert wins for a new identity, and the conflict branch returns the existing person. */
    @Override
    public UUID findOrCreatePerson(String issuer, String subject, String displayName, UUID newId, Instant now) {
        return query("INSERT INTO person (id, issuer, subject, display_name, created_at) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (issuer, subject) DO UPDATE SET display_name = EXCLUDED.display_name "
                        + "RETURNING id",
                statement -> {
                    statement.setObject(1, newId);
                    statement.setString(2, issuer);
                    statement.setString(3, subject);
                    statement.setString(4, displayName);
                    statement.setTimestamp(5, Timestamp.from(now));
                }, result -> {
                    result.next();
                    return result.getObject("id", UUID.class);
                });
    }

    @Override
    public void saveSession(Session session) {
        execute("INSERT INTO auth_session (secret_hash, person_id, roles, created_at, expires_at, last_seen_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setBytes(1, session.secretHash());
                    statement.setObject(2, session.personId());
                    statement.setArray(3, statement.getConnection().createArrayOf("text",
                            session.roles().stream().map(Role::name).sorted().toArray()));
                    statement.setTimestamp(4, Timestamp.from(session.createdAt()));
                    statement.setTimestamp(5, Timestamp.from(session.expiresAt()));
                    statement.setTimestamp(6, Timestamp.from(session.lastSeenAt()));
                });
    }

    /** Looks the session up and refreshes last_seen_at in the same statement, so an expired session is never touched. */
    @Override
    public Optional<Session> findSession(byte[] secretHash, Instant now) {
        return query("UPDATE auth_session AS s SET last_seen_at = ? FROM person AS p "
                        + "WHERE s.secret_hash = ? AND s.expires_at > ? AND p.id = s.person_id "
                        + "RETURNING s.person_id, p.subject, s.roles, s.created_at, s.expires_at",
                statement -> {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setBytes(2, secretHash);
                    statement.setTimestamp(3, Timestamp.from(now));
                }, result -> {
                    if (!result.next()) {
                        return Optional.<Session>empty();
                    }
                    return Optional.of(new Session(secretHash, result.getObject("person_id", UUID.class),
                            result.getString("subject"), rolesOf(result.getArray("roles")),
                            result.getTimestamp("created_at").toInstant(),
                            result.getTimestamp("expires_at").toInstant(), now));
                });
    }

    @Override
    public void deleteSession(byte[] secretHash) {
        execute("DELETE FROM auth_session WHERE secret_hash = ?", statement -> statement.setBytes(1, secretHash));
    }

    private static Set<Role> rolesOf(java.sql.Array array) throws SQLException {
        Object[] names = (Object[]) array.getArray();
        return Arrays.stream(names).map(Object::toString).map(Role::parse).flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void execute(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("the sign-in store failed", e);
        }
    }

    private <T> T query(String sql, Binder binder, Reader<T> reader) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return reader.read(result);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("the sign-in store failed", e);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(ResultSet result) throws SQLException;
    }
}
