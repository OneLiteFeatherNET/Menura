package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.db.Migrations;
import net.onelitefeather.menura.core.secret.SecretHasher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sign-in store against a fresh PostgreSQL 17 container with the real migrations applied. */
@Tag("integration")
@Testcontainers
class JdbcAuthStoreIntegrationTest {

    private static final String ISSUER = "https://login.example.test/tenant/v2.0";
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.11"))
            .withDatabaseName("menura")
            .withUsername("menura")
            .withPassword("menura");

    private JdbcAuthStore store() {
        Migrations.migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return new JdbcAuthStore(dataSource);
    }

    @Test
    void signInCanBeTakenExactlyOnce() {
        JdbcAuthStore store = store();
        byte[] state = SecretHasher.hash("state-1");
        store.saveSignIn(new AuthStore.SignIn(state, "nonce-1", "verifier-1", NOW.plusSeconds(600)));

        Optional<AuthStore.SignIn> first = store.takeSignIn(state, NOW);
        Optional<AuthStore.SignIn> second = store.takeSignIn(state, NOW);

        assertTrue(first.isPresent(), "a live sign-in must be returned once");
        assertEquals("verifier-1", first.get().codeVerifier());
        assertFalse(second.isPresent(), "a sign-in must not be returned a second time");
    }

    @Test
    void expiredSignInIsNotReturned() {
        JdbcAuthStore store = store();
        byte[] state = SecretHasher.hash("state-2");
        store.saveSignIn(new AuthStore.SignIn(state, "nonce-2", "verifier-2", NOW.minusSeconds(1)));

        assertFalse(store.takeSignIn(state, NOW).isPresent(), "an expired sign-in must never be accepted");
    }

    @Test
    void sameEntraIdentityIsTheSamePersonAcrossSignIns() {
        JdbcAuthStore store = store();

        UUID first = store.findOrCreatePerson(ISSUER, "subject-1", "First Name", UUID.randomUUID(), NOW);
        UUID second = store.findOrCreatePerson(ISSUER, "subject-1", "Renamed", UUID.randomUUID(), NOW);

        assertEquals(first, second, "a returning person keeps the person id of the first sign-in");
    }

    @Test
    void sessionCarriesThePersonAndTheRolesAndIsFoundUntilItsExpiry() {
        JdbcAuthStore store = store();
        UUID person = store.findOrCreatePerson(ISSUER, "subject-2", "Person", UUID.randomUUID(), NOW);
        byte[] digest = SecretHasher.hash("cookie-secret");
        store.saveSession(new AuthStore.Session(digest, person, "subject-2", Set.of(Role.VIEWER, Role.EDITOR),
                NOW, NOW.plusSeconds(3600), NOW));

        AuthStore.Session found = store.findSession(digest, NOW.plusSeconds(60)).orElseThrow();

        assertEquals(person, found.personId());
        assertEquals("subject-2", found.subject());
        assertEquals(Set.of(Role.VIEWER, Role.EDITOR), found.roles());
        assertEquals(NOW.plusSeconds(60), found.lastSeenAt(), "using a session refreshes its last-seen time");
        assertFalse(store.findSession(digest, NOW.plusSeconds(3600)).isPresent(),
                "a session stops at its expiry instant");
    }

    @Test
    void deletedSessionIsGone() {
        JdbcAuthStore store = store();
        UUID person = store.findOrCreatePerson(ISSUER, "subject-3", "Person", UUID.randomUUID(), NOW);
        byte[] digest = SecretHasher.hash("cookie-secret-2");
        store.saveSession(new AuthStore.Session(digest, person, "subject-3", Set.of(Role.VIEWER), NOW,
                NOW.plusSeconds(60), NOW));

        store.deleteSession(digest);

        assertFalse(store.findSession(digest, NOW).isPresent());
    }

    @Test
    void sessionRowHoldsOnlyTheDigestAndNoMenuraToken() throws SQLException {
        JdbcAuthStore store = store();
        String cookie = "plain-cookie-value";
        UUID person = store.findOrCreatePerson(ISSUER, "subject-4", "Person", UUID.randomUUID(), NOW);
        store.saveSession(new AuthStore.Session(SecretHasher.hash(cookie), person, "subject-4",
                Set.of(Role.VIEWER), NOW, NOW.plusSeconds(60), NOW));

        try (Connection connection = postgres.createConnection("");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT secret_hash FROM menura.auth_session");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertArrayEquals(SecretHasher.hash(cookie), result.getBytes("secret_hash"),
                    "the table holds the SHA-256 digest of the secret");
            assertFalse(new String(result.getBytes("secret_hash")).contains(cookie),
                    "the plain secret must not appear in the table");
        }
        try (Connection connection = postgres.createConnection("");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'menura' AND table_name = 'auth_session'");
             ResultSet columns = statement.executeQuery()) {
            while (columns.next()) {
                assertFalse(columns.getString(1).contains("token"),
                        "no access or refresh token is stored at rest: found column " + columns.getString(1));
            }
        }
    }
}
