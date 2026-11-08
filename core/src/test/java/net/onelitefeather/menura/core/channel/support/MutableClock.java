package net.onelitefeather.menura.core.channel.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the test moves forward explicitly. Nothing in a test reads the system time. */
public final class MutableClock extends Clock {

    private Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void advance(Duration duration) {
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
