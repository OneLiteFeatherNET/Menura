package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.auth.AuthStore;
import net.onelitefeather.menura.server.auth.JdbcAuthStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sign-in store of the prototype: the same rules as {@link JdbcAuthStore} (single-use sign-in transactions,
 * expiry, digests only, one person per Entra ID identity) kept in memory. Sessions end when the server restarts.
 * Active only in the prototype environment.
 *
 * <p>The memory is bounded: every save first removes the expired sign-ins and sessions, and a save that would exceed
 * {@link #MAX_SIGN_INS} or {@link #MAX_SESSIONS} evicts the oldest entries first. Time comes from the injected clock.
 */
@Singleton
@Replaces(JdbcAuthStore.class)
@Requires(env = "prototype")
public class PrototypeAuthStore implements AuthStore {

    static final int MAX_SIGN_INS = 10_000;
    static final int MAX_SESSIONS = 5_000;

    private final Clock clock;
    private final int maxSignIns;
    private final int maxSessions;
    /** Insertion order is the age order: the first entry is the oldest. Guarded by this store's lock. */
    private final Map<String, SignIn> signIns = new LinkedHashMap<>();
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final Map<String, UUID> people = new ConcurrentHashMap<>();

    @Inject
    public PrototypeAuthStore(Clock clock) {
        this(clock, MAX_SIGN_INS, MAX_SESSIONS);
    }

    PrototypeAuthStore(Clock clock, int maxSignIns, int maxSessions) {
        this.clock = clock;
        this.maxSignIns = maxSignIns;
        this.maxSessions = maxSessions;
    }

    @Override
    public synchronized void saveSignIn(SignIn signIn) {
        Instant now = clock.instant();
        signIns.values().removeIf(pending -> !pending.expiresAt().isAfter(now));
        String key = key(signIn.stateHash());
        signIns.remove(key);
        evictOldest(signIns, maxSignIns);
        signIns.put(key, signIn);
    }

    @Override
    public synchronized Optional<SignIn> takeSignIn(byte[] stateHash, Instant now) {
        SignIn taken = signIns.remove(key(stateHash));
        return taken == null || !taken.expiresAt().isAfter(now) ? Optional.empty() : Optional.of(taken);
    }

    @Override
    public UUID findOrCreatePerson(String issuer, String subject, String displayName, UUID newId, Instant now) {
        return people.computeIfAbsent(issuer + "|" + subject, identity -> newId);
    }

    @Override
    public synchronized void saveSession(Session session) {
        Instant now = clock.instant();
        sessions.values().removeIf(live -> !live.expiresAt().isAfter(now));
        String key = key(session.secretHash());
        sessions.remove(key);
        evictOldest(sessions, maxSessions);
        sessions.put(key, session);
    }

    /** Refreshes the last-seen time of a live session atomically; an expired session is never returned. */
    @Override
    public synchronized Optional<Session> findSession(byte[] secretHash, Instant now) {
        String key = key(secretHash);
        Session found = sessions.get(key);
        if (found == null || !found.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Session touched = new Session(found.secretHash(), found.personId(), found.subject(), found.roles(),
                found.createdAt(), found.expiresAt(), now);
        sessions.put(key, touched);
        return Optional.of(touched);
    }

    @Override
    public synchronized void deleteSession(byte[] secretHash) {
        sessions.remove(key(secretHash));
    }

    synchronized int pendingSignIns() {
        return signIns.size();
    }

    synchronized int sessionCount() {
        return sessions.size();
    }

    /** Removes the oldest entries until one more can be added without exceeding the capacity. */
    private static <V> void evictOldest(Map<String, V> entries, int capacity) {
        Iterator<String> oldestFirst = entries.keySet().iterator();
        while (entries.size() >= capacity && oldestFirst.hasNext()) {
            oldestFirst.next();
            oldestFirst.remove();
        }
    }

    private static String key(byte[] digest) {
        return Base64.getEncoder().encodeToString(digest);
    }
}
