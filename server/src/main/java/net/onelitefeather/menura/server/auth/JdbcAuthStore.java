package net.onelitefeather.menura.server.auth;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** PostgreSQL implementation of {@link AuthStore}, over the tables of migration V2 (auth_login, auth_session). */
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

    @Override
    public void saveSession(Session session) {
        execute("INSERT INTO auth_session (secret_hash, subject, access_token, expires_at) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setBytes(1, session.secretHash());
                    statement.setString(2, session.subject());
                    statement.setString(3, session.accessToken());
                    statement.setTimestamp(4, Timestamp.from(session.expiresAt()));
                });
    }

    @Override
    public Optional<Session> findSession(byte[] secretHash, Instant now) {
        return query("SELECT subject, access_token, expires_at FROM auth_session "
                        + "WHERE secret_hash = ? AND expires_at > ?",
                statement -> {
                    statement.setBytes(1, secretHash);
                    statement.setTimestamp(2, Timestamp.from(now));
                }, result -> {
                    if (!result.next()) {
                        return Optional.<Session>empty();
                    }
                    return Optional.of(new Session(secretHash, result.getString("subject"),
                            result.getString("access_token"), result.getTimestamp("expires_at").toInstant()));
                });
    }

    @Override
    public void deleteSession(byte[] secretHash) {
        execute("DELETE FROM auth_session WHERE secret_hash = ?", statement -> statement.setBytes(1, secretHash));
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
