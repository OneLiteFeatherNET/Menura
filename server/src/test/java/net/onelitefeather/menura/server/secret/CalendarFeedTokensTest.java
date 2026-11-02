package net.onelitefeather.menura.server.secret;

import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.AuditLog;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.audit.InMemoryAuditRepository;
import net.onelitefeather.menura.server.db.DirectTransactions;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.support.DeterministicRandom;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarFeedTokensTest {

    private static final UUID PERSON = UUID.fromString("5d2c1b0a-9f8e-4d7c-8b6a-1234567890ab");
    private static final Caller CALLER = new Caller(PERSON, Channel.WEB);
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final DirectTransactions transactions = new DirectTransactions();
    private final InMemoryCalendarFeedTokenRepository tokens = new InMemoryCalendarFeedTokenRepository();
    private final InMemoryAuditRepository audit = new InMemoryAuditRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CalendarFeedTokens service() {
        SecureRandom random = new DeterministicRandom();
        AuditLog auditLog = new AuditLog(transactions, clock, new IdentifierGenerator(random), audit);
        return new CalendarFeedTokens(transactions, clock, new IdentifierGenerator(random),
                new SecretGenerator(random), tokens, auditLog);
    }

    @Test
    void issuedTokenIsIdentifierDotSecretAndTheStoredRowHoldsOnlyTheHashOfTheSecret() throws SQLException {
        String token = service().issue(CALLER, "req-1");

        String secret = secretOf(token);
        assertEquals(1, tokens.rows().size(), "issuing must store exactly one feed token");
        FeedToken row = tokens.rows().get(0);
        assertEquals(PERSON, row.personId());
        assertArrayEquals(SecretHasher.hash(secret), row.secretHash(),
                "only the SHA-256 hash of the secret may be stored");
        assertEquals(token.substring(0, token.indexOf('.')), row.id().toString(),
                "the identifier part of the token names the stored row");
    }

    @Test
    void issuedTokenAuthenticatesItsPerson() throws SQLException {
        CalendarFeedTokens service = service();
        String token = service.issue(CALLER, "req-1");

        assertEquals(PERSON, service.authenticate(token));
    }

    @Test
    void rotationRefusesThePreviousTokenAtOnce() throws SQLException {
        CalendarFeedTokens service = service();
        String previous = service.issue(CALLER, "req-1");
        String current = service.issue(CALLER, "req-2");

        assertUnauthorized(() -> service.authenticate(previous));
        assertEquals(PERSON, service.authenticate(current), "the new token must work");
    }

    @Test
    void revokedTokenIsRefused() throws SQLException {
        CalendarFeedTokens service = service();
        String token = service.issue(CALLER, "req-1");

        service.revoke(CALLER, "req-2");

        assertUnauthorized(() -> service.authenticate(token));
    }

    @Test
    void tokenWithAnUnknownIdentifierIsRefused() throws SQLException {
        CalendarFeedTokens service = service();
        service.issue(CALLER, "req-1");
        String unknown = UUID.randomUUID() + "." + "A".repeat(43);

        assertUnauthorized(() -> service.authenticate(unknown));
    }

    @Test
    void tokenWithTheWrongSecretIsRefused() throws SQLException {
        CalendarFeedTokens service = service();
        String token = service.issue(CALLER, "req-1");
        String forged = token.substring(0, token.indexOf('.') + 1) + "B".repeat(43);

        assertUnauthorized(() -> service.authenticate(forged));
    }

    @Test
    void malformedTokenIsRefusedWithoutLookingItUp() {
        CalendarFeedTokens service = service();

        assertUnauthorized(() -> service.authenticate("not-a-token"));
        assertUnauthorized(() -> service.authenticate(null));
    }

    @Test
    void issueWritesOneAuditEntryNamingPersonActionTargetChannelAndRequest() throws SQLException {
        service().issue(CALLER, "req-1");

        assertEquals(1, audit.entries().size(), "issuing a token is a write and must be audited once");
        AuditEntry entry = audit.entries().get(0);
        assertEquals(PERSON, entry.personId());
        assertEquals("calendar_feed_token.issue", entry.action());
        assertEquals("person:" + PERSON, entry.target());
        assertEquals(Channel.WEB, entry.channel());
        assertEquals("req-1", entry.requestId());
        assertEquals(NOW, entry.occurredAt());
    }

    @Test
    void issueWritesTheTokenRowAndItsAuditEntryInOneTransaction() throws SQLException {
        service().issue(CALLER, "req-1");

        assertEquals(1, transactions.connections().size(), "the token and its audit entry must share one transaction");
        assertSame(transactions.connections().get(0), tokens.connections().get(0));
        assertSame(transactions.connections().get(0), audit.connections().get(0));
    }

    @Test
    void revokeWritesAnAuditEntry() throws SQLException {
        CalendarFeedTokens service = service();
        service.issue(CALLER, "req-1");

        service.revoke(CALLER, "req-2");

        assertEquals("calendar_feed_token.revoke", audit.entries().get(1).action());
        assertEquals("req-2", audit.entries().get(1).requestId());
    }

    @Test
    void revokeWithoutAnyTokenIsNotAnError() throws SQLException {
        service().revoke(CALLER, "req-1");

        assertTrue(tokens.rows().isEmpty());
    }

    private static String secretOf(String token) {
        return token.substring(token.indexOf('.') + 1);
    }

    private static void assertUnauthorized(ThrowingCall call) {
        ProblemException failure = assertThrows(ProblemException.class, call::run);
        assertEquals(401, failure.status(), "an unknown, revoked or rotated token must answer 401");
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws SQLException;
    }
}
