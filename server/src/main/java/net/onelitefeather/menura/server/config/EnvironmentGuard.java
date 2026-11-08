package net.onelitefeather.menura.server.config;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;

import java.util.Collection;

/**
 * Refuses to start when the test environment {@code e2e} is active together with {@code prod}. The e2e environment
 * serves the test-only sign-in, which must never run in production, so a misconfigured deployment fails at boot.
 * The check runs at startup, before the HTTP server accepts a request.
 */
@Singleton
public class EnvironmentGuard implements ApplicationEventListener<StartupEvent> {

    static final String E2E = "e2e";
    static final String PROD = "prod";

    private final Environment environment;

    public EnvironmentGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        refuseCombined(environment.getActiveNames());
    }

    /** Throws when both environments are active; names both in the message. */
    public static void refuseCombined(Collection<String> activeEnvironments) {
        if (activeEnvironments.contains(E2E) && activeEnvironments.contains(PROD)) {
            throw new IllegalStateException("The environments '" + E2E + "' and '" + PROD + "' must not be active "
                    + "together: '" + E2E + "' serves the test-only sign-in. Remove one of them from the environment.");
        }
    }
}
