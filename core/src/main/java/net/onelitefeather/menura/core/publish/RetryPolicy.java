package net.onelitefeather.menura.core.publish;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How transient publication failures are retried: at most {@code maxAttempts} attempts, and a delay that doubles with each
 * attempt from {@code baseDelay}, never longer than six hours. The policy is pure; the caller reads the time.
 */
public record RetryPolicy(int maxAttempts, Duration baseDelay) {

    static final Duration CAP = Duration.ofHours(6);

    public RetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("the attempt limit must be between 1 and 10");
        }
        Objects.requireNonNull(baseDelay, "baseDelay");
        if (baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("the retry delay must be positive");
        }
    }

    /** Whether another attempt is allowed after {@code attempts} attempts have been made. */
    public boolean mayRetry(int attempts) {
        return attempts < maxAttempts;
    }

    /** The time of the next attempt after the given number of attempts; the delay doubles with each attempt. */
    public Instant nextAttempt(int attempts, Instant now) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = baseDelay.multipliedBy(1L << exponent);
        if (delay.compareTo(CAP) > 0) {
            delay = CAP;
        }
        return now.plus(delay);
    }
}
