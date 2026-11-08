package net.onelitefeather.menura.server.secret;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.security.Caller;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calendar feed tokens of the people who subscribe to their planned posts in a calendar client. A token is
 * {@code <id>.<secret>}: the id names the stored row, the secret is 256 random bits shown once and kept only as its
 * hash. Each person has at most one active token; issuing a new one rotates the old one out at once.
 */
@Singleton
public class CalendarFeedTokens {

    private static final Pattern TOKEN = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.([A-Za-z0-9_-]{43})");

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final SecretGenerator secrets;
    private final CalendarFeedTokenRepository repository;
    private final AuditLog auditLog;

    public CalendarFeedTokens(Transactions transactions, Clock clock, IdentifierGenerator identifiers,
                              SecretGenerator secrets, CalendarFeedTokenRepository repository, AuditLog auditLog) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.secrets = secrets;
        this.repository = repository;
        this.auditLog = auditLog;
    }

    /**
     * Issues a new token and retires the previous one, in one audited transaction. The returned token is the only
     * time the secret leaves the server.
     */
    public String issue(Caller caller, String requestId) throws SQLException {
        return issue(caller, requestId, false);
    }

    /** Issues a token that carries the publisher role of its person into the feed (approvals due). */
    public String issue(Caller caller, String requestId, boolean publisher) throws SQLException {
        UUID id = identifiers.next();
        String secret = secrets.next();
        Instant now = clock.instant();
        return auditLog.write(caller, "calendar_feed_token.issue", target(caller), requestId, connection -> {
            repository.revokeActive(connection, caller.personId(), now);
            repository.insert(connection, new FeedToken(id, caller.personId(), SecretHasher.hash(secret), now, null,
                    publisher));
            return id + "." + secret;
        });
    }

    /** What a valid feed token grants: the token's identifier for rate limiting, the person and the publisher flag. */
    public record FeedAccess(UUID tokenId, UUID personId, boolean publisher) {
    }

    /** The access a presented feed token grants. Unknown, malformed, revoked and rotated tokens answer 401 here. */
    public FeedAccess authenticateFeed(String presented) throws SQLException {
        FeedToken stored = verify(presented);
        return new FeedAccess(stored.id(), stored.personId(), stored.publisher());
    }

    /** Revokes the person's active token, if any. The revocation is audited even when there was nothing to revoke. */
    public void revoke(Caller caller, String requestId) throws SQLException {
        Instant now = clock.instant();
        auditLog.write(caller, "calendar_feed_token.revoke", target(caller), requestId, connection -> {
            repository.revokeActive(connection, caller.personId(), now);
            return null;
        });
    }

    /**
     * The person a presented token belongs to. An unknown, malformed, revoked or rotated token answers 401; the
     * secret is compared through its hash in constant time.
     */
    public UUID authenticate(String presented) throws SQLException {
        return verify(presented).personId();
    }

    private FeedToken verify(String presented) throws SQLException {
        Matcher token = presented == null ? null : TOKEN.matcher(presented);
        if (token == null || !token.matches()) {
            throw unauthorized();
        }
        UUID id = UUID.fromString(token.group(1));
        String secret = token.group(2);
        Optional<FeedToken> stored = transactions.inTransaction(connection -> repository.findActive(connection, id));
        if (stored.isEmpty() || !SecretHasher.matches(secret, stored.get().secretHash())) {
            throw unauthorized();
        }
        return stored.get();
    }

    private static String target(Caller caller) {
        return "person:" + caller.personId();
    }

    private static ProblemException unauthorized() {
        return new ProblemException(401, "Unauthorized", "The calendar feed token is unknown, revoked or rotated.");
    }
}
