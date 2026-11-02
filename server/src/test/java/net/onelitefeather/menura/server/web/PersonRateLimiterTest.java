package net.onelitefeather.menura.server.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonRateLimiterTest {

    private static final Instant START = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void requestsWithinTheBudgetAreAllowed() {
        PersonRateLimiter limiter = new PersonRateLimiter(new ManualClock(START), 3, Duration.ofMinutes(1));

        assertTrue(limiter.tryAcquire("person-a").allowed());
        assertTrue(limiter.tryAcquire("person-a").allowed());
        assertTrue(limiter.tryAcquire("person-a").allowed());
    }

    @Test
    void requestBeyondTheBudgetIsRefusedWithRetryAfter() {
        PersonRateLimiter limiter = new PersonRateLimiter(new ManualClock(START), 2, Duration.ofMinutes(1));
        limiter.tryAcquire("person-a");
        limiter.tryAcquire("person-a");

        PersonRateLimiter.Decision refused = limiter.tryAcquire("person-a");

        assertFalse(refused.allowed(), "the third request in a one-minute window with budget 2 must be refused");
        assertEquals(30, refused.retryAfterSeconds(), "one token refills every 30 seconds at budget 2 per minute");
    }

    @Test
    void budgetRefillsWithTheInjectedClock() {
        ManualClock clock = new ManualClock(START);
        PersonRateLimiter limiter = new PersonRateLimiter(clock, 2, Duration.ofMinutes(1));
        limiter.tryAcquire("person-a");
        limiter.tryAcquire("person-a");
        assertFalse(limiter.tryAcquire("person-a").allowed());

        clock.advance(Duration.ofSeconds(30));

        assertTrue(limiter.tryAcquire("person-a").allowed(), "after the refill interval one request is allowed again");
    }

    @Test
    void budgetsOfDifferentPersonsAreIndependent() {
        PersonRateLimiter limiter = new PersonRateLimiter(new ManualClock(START), 1, Duration.ofMinutes(1));
        limiter.tryAcquire("person-a");

        assertTrue(limiter.tryAcquire("person-b").allowed(), "one person exhausting the budget must not limit another");
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        PersonRateLimiter limiter = new PersonRateLimiter(new ManualClock(START), 1, Duration.ofHours(1));
        limiter.tryAcquire("person-a");

        PersonRateLimiter.Decision refused = limiter.tryAcquire("person-a");

        assertTrue(refused.retryAfterSeconds() >= 1, "Retry-After must never be zero for a refused request");
    }
}
