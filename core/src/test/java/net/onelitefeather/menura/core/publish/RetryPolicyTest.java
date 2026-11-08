package net.onelitefeather.menura.core.publish;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final RetryPolicy POLICY = new RetryPolicy(3, Duration.ofMinutes(5));

    @Test
    void retriesStopAtTheConfiguredLimit() {
        assertTrue(POLICY.mayRetry(2), "a post that made two attempts may try a third time");
        assertFalse(POLICY.mayRetry(3), "a post that made three attempts is not tried again");
    }

    @Test
    void theDelayGrowsWithEachAttempt() {
        assertEquals(NOW.plus(Duration.ofMinutes(5)), POLICY.nextAttempt(1, NOW), "the first retry waits the base delay");
        assertEquals(NOW.plus(Duration.ofMinutes(10)), POLICY.nextAttempt(2, NOW), "the second retry waits twice as long");
        assertEquals(NOW.plus(Duration.ofMinutes(20)), POLICY.nextAttempt(3, NOW), "the third retry waits four times as long");
    }

    @Test
    void theDelayNeverExceedsSixHours() {
        assertEquals(NOW.plus(Duration.ofHours(6)), POLICY.nextAttempt(30, NOW), "a long run of attempts is capped");
    }

    @Test
    void anAttemptLimitOutsideTheAllowedRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(0, Duration.ofMinutes(1)),
                "zero attempts is refused");
    }
}
