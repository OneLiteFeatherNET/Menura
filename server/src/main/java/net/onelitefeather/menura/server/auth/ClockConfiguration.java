package net.onelitefeather.menura.server.auth;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * The application clock. Every time-dependent rule (token expiry, sign-in transactions, session lifetime) reads time
 * from this bean, so tests replace it with a fixed or advanced clock instead of waiting. A test that provides its own
 * {@link Clock} bean takes the place of this default.
 */
@Factory
public class ClockConfiguration {

    @Singleton
    @Requires(missingBeans = Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }
}
