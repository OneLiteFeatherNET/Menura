package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.auth.AuthStore;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test builds its own store on its own clock, so the order of the tests does not matter. */
class PrototypeAuthStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Instant FAR_FUTURE = NOW.plus(Duration.ofDays(365));

    /** A clock the test moves forward; it never reads the system time. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static byte[] digest(int number) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(number).array();
    }

    private static AuthStore.SignIn signIn(int number, Instant expiresAt) {
        return new AuthStore.SignIn(digest(number), "nonce-" + number, "verifier-" + number, expiresAt);
    }

    private static AuthStore.Session session(int number, Instant expiresAt) {
        return new AuthStore.Session(digest(number), UUID.nameUUIDFromBytes(("person-" + number).getBytes()),
                "subject-" + number, Set.of(Role.VIEWER), NOW, expiresAt, NOW);
    }

    // Expiry

    @Test
    void expiredSignInsArePurgedOnTheNextSave() {
        MovableClock clock = new MovableClock(NOW);
        PrototypeAuthStore store = new PrototypeAuthStore(clock);
        store.saveSignIn(signIn(1, NOW.plus(Duration.ofMinutes(10))));

        clock.advance(Duration.ofMinutes(11));
        store.saveSignIn(signIn(2, FAR_FUTURE));

        assertEquals(1, store.pendingSignIns(), "only the live sign-in remains after the purge");
        assertEquals(Optional.empty(), store.takeSignIn(digest(1), clock.instant()));
    }

    @Test
    void expiredSessionsArePurgedOnTheNextSave() {
        MovableClock clock = new MovableClock(NOW);
        PrototypeAuthStore store = new PrototypeAuthStore(clock);
        store.saveSession(session(1, NOW.plus(Duration.ofHours(8))));

        clock.advance(Duration.ofHours(9));
        store.saveSession(session(2, FAR_FUTURE));

        assertEquals(1, store.sessionCount(), "only the live session remains after the purge");
    }

    @Test
    void aSignInExpiringExactlyNowIsPurgedLikeAnExpiredOne() {
        MovableClock clock = new MovableClock(NOW);
        PrototypeAuthStore store = new PrototypeAuthStore(clock);
        store.saveSignIn(signIn(1, NOW));

        store.saveSignIn(signIn(2, FAR_FUTURE));

        assertEquals(1, store.pendingSignIns());
    }

    // Caps

    @Test
    void theSignInCapEvictsTheOldestSignInFirst() {
        PrototypeAuthStore store = new PrototypeAuthStore(new MovableClock(NOW));
        for (int number = 1; number <= PrototypeAuthStore.MAX_SIGN_INS; number++) {
            store.saveSignIn(signIn(number, FAR_FUTURE));
        }

        store.saveSignIn(signIn(PrototypeAuthStore.MAX_SIGN_INS + 1, FAR_FUTURE));

        assertEquals(PrototypeAuthStore.MAX_SIGN_INS, store.pendingSignIns());
        assertEquals(Optional.empty(), store.takeSignIn(digest(1), NOW), "the oldest sign-in was evicted");
        assertTrue(store.takeSignIn(digest(2), NOW).isPresent(), "the second sign-in is still pending");
    }

    @Test
    void theSessionCapEvictsTheOldestSessionFirst() {
        PrototypeAuthStore store = new PrototypeAuthStore(new MovableClock(NOW));
        for (int number = 1; number <= PrototypeAuthStore.MAX_SESSIONS; number++) {
            store.saveSession(session(number, FAR_FUTURE));
        }

        store.saveSession(session(PrototypeAuthStore.MAX_SESSIONS + 1, FAR_FUTURE));

        assertEquals(PrototypeAuthStore.MAX_SESSIONS, store.sessionCount());
        assertEquals(Optional.empty(), store.findSession(digest(1), NOW), "the oldest session was evicted");
        assertTrue(store.findSession(digest(2), NOW).isPresent(), "the second session is still there");
    }

    @Test
    void theCapsAreTenThousandSignInsAndFiveThousandSessions() {
        assertEquals(10_000, PrototypeAuthStore.MAX_SIGN_INS);
        assertEquals(5_000, PrototypeAuthStore.MAX_SESSIONS);
    }

    // Behaviour that stays

    @Test
    void aValidSessionStillResolvesAfterOtherSessionsWereSavedAndPurged() {
        MovableClock clock = new MovableClock(NOW);
        PrototypeAuthStore store = new PrototypeAuthStore(clock);
        store.saveSession(session(1, FAR_FUTURE));
        store.saveSession(session(2, NOW.plus(Duration.ofMinutes(5))));

        clock.advance(Duration.ofMinutes(6));
        store.saveSession(session(3, FAR_FUTURE));

        Optional<AuthStore.Session> found = store.findSession(digest(1), clock.instant());
        assertTrue(found.isPresent(), "the live session still resolves");
        assertEquals(clock.instant(), found.get().lastSeenAt(), "using it refreshes the last-seen time");
        assertFalse(store.findSession(digest(2), clock.instant()).isPresent(), "the expired one does not resolve");
    }

    @Test
    void aSignInCanBeTakenOnlyOnce() {
        PrototypeAuthStore store = new PrototypeAuthStore(new MovableClock(NOW));
        store.saveSignIn(signIn(1, FAR_FUTURE));

        assertTrue(store.takeSignIn(digest(1), NOW).isPresent());
        assertEquals(Optional.empty(), store.takeSignIn(digest(1), NOW));
    }
}
