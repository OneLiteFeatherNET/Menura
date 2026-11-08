package net.onelitefeather.menura.server.secret;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored calendar feed token. It holds the SHA-256 hash of the secret part of the token, never the secret. A
 * revoked row (rotated or revoked) stays in the table with {@code revokedAt} set, and is never active again.
 * {@code publisher} is the publisher role of the person when the token was issued: the feed shows the approvals due only
 * to such a token, and a person who gains or loses the role gets a new token.
 */
public record FeedToken(UUID id, UUID personId, byte[] secretHash, Instant createdAt, Instant revokedAt,
                        boolean publisher) {

    public FeedToken revokedAt(Instant at) {
        return new FeedToken(id, personId, secretHash, createdAt, at, publisher);
    }
}
