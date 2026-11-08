package net.onelitefeather.menura.server.channel;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server does not start without the token encryption key, and the failure names the variable to set. */
class TokenKeyStartupTest {

    @Test
    void startupStopsWithAClearMessageWhenTheTokenKeyIsMissing() {
        Exception failure = assertThrows(Exception.class, () -> ApplicationContext.builder()
                .environments("test")
                .properties(Map.of("menura.channels.token-encryption-key", ""))
                .start()
                .close());

        String chain = causeChain(failure);
        assertTrue(chain.contains("MENURA_TOKEN_ENCRYPTION_KEY"),
                "the startup failure names the variable the operator must set, but was: " + chain);
    }

    private static String causeChain(Throwable failure) {
        StringBuilder out = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            out.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage()).append(" | ");
        }
        return out.toString();
    }
}
