package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;

/** The one in-memory demo store of the prototype environment. Exists only when {@code prototype} is active. */
@Factory
@Requires(env = "prototype")
public class PrototypeConfiguration {

    @Singleton
    DemoStore demoStore(Clock clock) {
        return new DemoStore(clock);
    }
}
