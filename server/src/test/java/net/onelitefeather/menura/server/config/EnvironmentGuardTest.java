package net.onelitefeather.menura.server.config;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentGuardTest {

    @Test
    void combinedE2eAndProdIsRefusedAndTheMessageNamesBothEnvironments() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> EnvironmentGuard.refuseCombined(Set.of("e2e", "prod")));

        assertTrue(refused.getMessage().contains("e2e"), "the message names e2e");
        assertTrue(refused.getMessage().contains("prod"), "the message names prod");
    }

    @Test
    void e2eAloneIsAllowed() {
        assertDoesNotThrow(() -> EnvironmentGuard.refuseCombined(List.of("e2e", "local")));
    }

    @Test
    void prodAloneIsAllowed() {
        assertDoesNotThrow(() -> EnvironmentGuard.refuseCombined(List.of("prod")));
    }

    @Test
    void startingTheContextWithBothEnvironmentsFailsBeforeAnyRequestIsServed() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> {
            try (ApplicationContext context = ApplicationContext.builder().environments("e2e", "prod").start()) {
                throw new AssertionError("the context must not start with e2e and prod together");
            }
        }, "the context must refuse to start");

        assertTrue(refused.getMessage().contains("e2e") && refused.getMessage().contains("prod"),
                "the startup error names both environments");
    }
}
