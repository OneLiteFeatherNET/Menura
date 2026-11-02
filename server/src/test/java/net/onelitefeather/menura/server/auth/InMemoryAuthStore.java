package net.onelitefeather.menura.server.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test replacement of the PostgreSQL store: the same rules (single use, expiry, digests only) in memory. Active only
 * in tests that set {@code menura.test.in-memory-auth}, so the JDBC store is still the one used everywhere else.
 */
@Singleton
@Replaces(JdbcAuthStore.class)
@Requires(property = "menura.test.in-memory-auth", value = "true")
class InMemoryAuthStore implements AuthStore {

    private final Map<String, SignIn> signIns = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public void saveSignIn(SignIn signIn) {
        signIns.put(key(signIn.stateHash()), signIn);
    }

    @Override
    public Optional<SignIn> takeSignIn(byte[] stateHash, Instant now) {
        SignIn taken = signIns.remove(key(stateHash));
        return taken == null || !taken.expiresAt().isAfter(now) ? Optional.empty() : Optional.of(taken);
    }

    @Override
    public void saveSession(Session session) {
        sessions.put(key(session.secretHash()), session);
    }

    @Override
    public Optional<Session> findSession(byte[] secretHash, Instant now) {
        Session found = sessions.get(key(secretHash));
        return found == null || !found.expiresAt().isAfter(now) ? Optional.empty() : Optional.of(found);
    }

    @Override
    public void deleteSession(byte[] secretHash) {
        sessions.remove(key(secretHash));
    }

    void clear() {
        signIns.clear();
        sessions.clear();
    }

    int sessionCount() {
        return sessions.size();
    }

    boolean hasSessionFor(byte[] secretHash) {
        return sessions.containsKey(key(secretHash));
    }

    private static String key(byte[] digest) {
        return Base64.getEncoder().encodeToString(digest);
    }
}
