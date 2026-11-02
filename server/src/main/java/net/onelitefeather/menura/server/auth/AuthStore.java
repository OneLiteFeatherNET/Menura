package net.onelitefeather.menura.server.auth;

import net.onelitefeather.menura.core.auth.Role;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side sign-in state and the person register. Every secret is held as its SHA-256 digest, so a read of the
 * store reveals no value that a browser or a caller could present. Expired rows are never returned. No Menura
 * access token is stored.
 */
public interface AuthStore {

    /** A sign-in in progress, keyed by the digest of its state parameter. */
    record SignIn(byte[] stateHash, String nonce, String codeVerifier, Instant expiresAt) {
    }

    /**
     * A web session, keyed by the digest of its cookie secret. It stands for a person with the roles granted at
     * sign-in; the roles are fixed for the session's lifetime.
     */
    record Session(byte[] secretHash, UUID personId, String subject, Set<Role> roles, Instant createdAt,
                   Instant expiresAt, Instant lastSeenAt) {
    }

    void saveSignIn(SignIn signIn);

    /** Returns the sign-in and removes it, so a state can be used once. Empty when unknown or expired. */
    Optional<SignIn> takeSignIn(byte[] stateHash, Instant now);

    /**
     * The person of an Entra ID identity: the existing one for the same issuer and subject, or a new person with the
     * given id. The display name is refreshed on every sign-in.
     */
    UUID findOrCreatePerson(String issuer, String subject, String displayName, UUID newId, Instant now);

    void saveSession(Session session);

    /**
     * The session of a digest while it has not expired. Using it refreshes its last-seen time, so the returned
     * session carries the time of this use.
     */
    Optional<Session> findSession(byte[] secretHash, Instant now);

    void deleteSession(byte[] secretHash);
}
