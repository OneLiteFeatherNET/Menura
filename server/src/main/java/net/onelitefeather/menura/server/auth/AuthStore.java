package net.onelitefeather.menura.server.auth;

import java.time.Instant;
import java.util.Optional;

/**
 * Server-side sign-in state. Every secret is held as its SHA-256 digest, so a read of the store reveals no value that
 * a browser or a caller could present. Expired rows are never returned.
 */
public interface AuthStore {

    /** A sign-in in progress, keyed by the digest of its state parameter. */
    record SignIn(byte[] stateHash, String nonce, String codeVerifier, Instant expiresAt) {
    }

    /** A web session, keyed by the digest of its cookie secret. It stands for one Menura access token. */
    record Session(byte[] secretHash, String subject, String accessToken, Instant expiresAt) {
    }

    void saveSignIn(SignIn signIn);

    /** Returns the sign-in and removes it, so a state can be used once. Empty when unknown or expired. */
    Optional<SignIn> takeSignIn(byte[] stateHash, Instant now);

    void saveSession(Session session);

    /** The session of a digest while it has not expired. */
    Optional<Session> findSession(byte[] secretHash, Instant now);

    void deleteSession(byte[] secretHash);
}
