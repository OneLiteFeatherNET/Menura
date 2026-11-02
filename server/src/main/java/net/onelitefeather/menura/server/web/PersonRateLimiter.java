package net.onelitefeather.menura.server.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-person request budget as a token bucket with integer arithmetic. A person may make {@code capacity}
 * requests per {@code window}; the bucket refills at {@code capacity} requests per window, continuously.
 * Time comes from the injected clock, so tests never wait and never depend on the wall clock.
 *
 * <p>The gateway limits per IP and per client (Envoy Gateway). The person budget lives here because only the
 * server sees the person from the validated token (see deploy/helm/menura/README.md, "Rate limits").
 */
public final class PersonRateLimiter {

    private final Clock clock;
    private final long capacity;
    private final long windowMillis;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public PersonRateLimiter(Clock clock, int capacity, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (window.toMillis() < 1) {
            throw new IllegalArgumentException("window must be at least one millisecond");
        }
        this.capacity = capacity;
        this.windowMillis = window.toMillis();
    }

    /** Takes one request from the person's budget, or says after how many seconds the next one is allowed. */
    public Decision tryAcquire(String person) {
        Objects.requireNonNull(person, "person");
        Instant now = clock.instant();
        return buckets.computeIfAbsent(person, key -> new Bucket(now)).take(now);
    }

    /** Outcome of one request: whether it may proceed, and the Retry-After seconds when it may not. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /*
     * Credit is measured in "capacity units": a request costs windowMillis, and the bucket gains capacity units
     * per elapsed millisecond. So a full bucket holds capacity * windowMillis units and one window refills it.
     */
    private final class Bucket {

        private long credit;
        private Instant updated;

        Bucket(Instant now) {
            this.credit = capacity * windowMillis;
            this.updated = now;
        }

        synchronized Decision take(Instant now) {
            long elapsedMillis = Math.max(0, Duration.between(updated, now).toMillis());
            credit = Math.min(capacity * windowMillis, credit + elapsedMillis * capacity);
            updated = now;
            if (credit >= windowMillis) {
                credit -= windowMillis;
                return new Decision(true, 0);
            }
            long deficit = windowMillis - credit;
            long waitMillis = ceilDivide(deficit, capacity);
            return new Decision(false, Math.max(1, ceilDivide(waitMillis, 1000)));
        }
    }

    private static long ceilDivide(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}
