package net.onelitefeather.menura.server.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the test moves by hand, so time-dependent behaviour never depends on the wall clock. */
final class ManualClock extends Clock {

    private Instant now;

    ManualClock(Instant start) {
        this.now = start;
    }

    void advance(Duration duration) {
        now = now.plus(duration);
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
