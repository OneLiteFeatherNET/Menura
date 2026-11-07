package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.auth.AuthStore;
import net.onelitefeather.menura.server.auth.JdbcAuthStore;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sign-in store of the prototype: the same rules as {@link JdbcAuthStore} (single-use sign-in transactions,
 * expiry, digests only, one person per Entra ID identity) kept in memory. Sessions end when the server restarts.
 * Active only in the prototype environment.
 */
@Singleton
@Replaces(JdbcAuthStore.class)
@Requires(env = "prototype")
public class PrototypeAuthStore implements AuthStore {

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

    /** Refreshes the last-seen time of a live session atomically; an expired session is never returned. */
    @Override
    public Optional<Session> findSession(byte[] secretHash, Instant now) {
        Session[] touched = new Session[1];
        sessions.computeIfPresent(key(secretHash), (k, found) -> {
            if (!found.expiresAt().isAfter(now)) {
                return found;
            }
            touched[0] = new Session(found.secretHash(), found.personId(), found.subject(), found.roles(),
                    found.createdAt(), found.expiresAt(), now);
            return touched[0];
        });
        return Optional.ofNullable(touched[0]);
    }

    @Override
    public void deleteSession(byte[] secretHash) {
        sessions.remove(key(secretHash));
    }

    private static String key(byte[] digest) {
        return Base64.getEncoder().encodeToString(digest);
    }
}
