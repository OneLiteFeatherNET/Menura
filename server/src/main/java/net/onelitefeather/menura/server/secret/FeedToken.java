package net.onelitefeather.menura.server.secret;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored calendar feed token. It holds the SHA-256 hash of the secret part of the token, never the secret. A
 * revoked row (rotated or revoked) stays in the table with {@code revokedAt} set, and is never active again.
 */
public record FeedToken(UUID id, UUID personId, byte[] secretHash, Instant createdAt, Instant revokedAt) {

    public FeedToken revokedAt(Instant at) {
        return new FeedToken(id, personId, secretHash, createdAt, at);
    }
}
