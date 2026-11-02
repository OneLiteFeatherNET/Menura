package net.onelitefeather.menura.server.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test replacement of the PostgreSQL store: the same rules (single use, expiry, digests only, one person per Entra ID
 * identity) in memory. Active only in tests that set {@code menura.test.in-memory-auth}, so the JDBC store is still the
 * one used everywhere else.
 */
@Singleton
@Replaces(JdbcAuthStore.class)
@Requires(property = "menura.test.in-memory-auth", value = "true")
class InMemoryAuthStore implements AuthStore {

    private final Map<String, SignIn> signIns = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> people = new ConcurrentHashMap<>();

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
    public UUID findOrCreatePerson(String issuer, String subject, String displayName, UUID newId, Instant now) {
        return people.computeIfAbsent(issuer + "|" + subject, identity -> newId);
    }

    @Override
    public void saveSession(Session session) {
        sessions.put(key(session.secretHash()), session);
    }

    @Override
    public Optional<Session> findSession(byte[] secretHash, Instant now) {
        Session found = sessions.get(key(secretHash));
        if (found == null || !found.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Session touched = new Session(found.secretHash(), found.personId(), found.subject(), found.roles(),
                found.createdAt(), found.expiresAt(), now);
        sessions.put(key(secretHash), touched);
        return Optional.of(touched);
    }

    @Override
    public void deleteSession(byte[] secretHash) {
        sessions.remove(key(secretHash));
    }

    void clear() {
        signIns.clear();
        sessions.clear();
        people.clear();
    }

    int sessionCount() {
        return sessions.size();
    }

    boolean hasSessionFor(byte[] secretHash) {
        return sessions.containsKey(key(secretHash));
    }

    /** The stored session of a digest, without refreshing it. */
    Optional<Session> storedSession(byte[] secretHash) {
        return Optional.ofNullable(sessions.get(key(secretHash)));
    }

    private static String key(byte[] digest) {
        return Base64.getEncoder().encodeToString(digest);
    }
}
