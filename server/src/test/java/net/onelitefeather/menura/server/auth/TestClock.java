package net.onelitefeather.menura.server.auth;

import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test clock that replaces the system clock bean. Time moves only when a test advances it, so expiry is exercised
 * without waiting. Each test resets it in its set-up.
 */
@Singleton
class TestClock extends Clock {

    static final Instant START = Instant.parse("2026-11-02T12:00:00Z");

    private volatile Instant now = START;

    synchronized void advance(Duration by) {
        now = now.plus(by);
    }

    synchronized void reset() {
        now = START;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
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
