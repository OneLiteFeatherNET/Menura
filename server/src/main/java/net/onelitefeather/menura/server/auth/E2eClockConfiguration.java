package net.onelitefeather.menura.server.auth;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The fixed clock of environment e2e. The end-to-end tests see the same "today" whatever the wall clock says, so a
 * calendar day in a test is always the same day. The instant comes from {@code MENURA_E2E_CLOCK} with a default.
 */
@Factory
@Requires(env = "e2e")
public class E2eClockConfiguration {

    @Singleton
    Clock clock(@Value("${menura.e2e.clock:`2026-11-09T10:00:00Z`}") String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
