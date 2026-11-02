package net.onelitefeather.menura.server.web;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;

/** Builds the person budget from {@code menura.ratelimit.person.*}; defaults match application.yml. */
@Factory
public class PersonRateLimitFactory {

    @Singleton
    PersonRateLimiter personRateLimiter(
            @Value("${menura.ratelimit.person.requests:600}") int requests,
            @Value("${menura.ratelimit.person.window-seconds:60}") long windowSeconds) {
        return new PersonRateLimiter(Clock.systemUTC(), requests, Duration.ofSeconds(windowSeconds));
    }
}
