package net.onelitefeather.menura.core.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MenuraSettingsTest {

    @Test
    void settingsUseLocalDevelopmentDefaultsWhenNoEnvironmentVariableIsSet() {
        MenuraSettings settings = MenuraSettings.from(Map.of());

        assertEquals("jdbc:postgresql://localhost:5432/menura", settings.databaseUrl(),
                "the default database URL must point at the docker-compose PostgreSQL service");
        assertEquals("menura", settings.databaseUser(), "the default database user must match docker-compose");
    }

    @Test
    void passwordIsEmptyWhenNoEnvironmentVariableIsSet() {
        MenuraSettings settings = MenuraSettings.from(Map.of());

        assertEquals("", settings.databasePassword(),
                "without MENURA_DB_PASSWORD the password must be empty, never a default literal");
    }

    @Test
    void environmentVariableOverridesDatabaseUrl() {
        MenuraSettings settings = MenuraSettings.from(Map.of("MENURA_DB_URL", "jdbc:postgresql://db.internal:5432/prod"));

        assertEquals("jdbc:postgresql://db.internal:5432/prod", settings.databaseUrl(),
                "MENURA_DB_URL must override the default database URL");
        assertEquals("menura", settings.databaseUser(), "an unset user must keep its default");
    }

    @Test
    void environmentVariablesOverrideDatabaseUserAndPassword() {
        String injectedPassword = "injected-" + UUID.randomUUID();
        MenuraSettings settings = MenuraSettings.from(Map.of(
                "MENURA_DB_USER", "menura_app",
                "MENURA_DB_PASSWORD", injectedPassword));

        assertEquals("menura_app", settings.databaseUser(), "MENURA_DB_USER must override the default user");
        assertEquals(injectedPassword, settings.databasePassword(), "MENURA_DB_PASSWORD must override the empty password");
        assertFalse(settings.databasePassword().isEmpty(), "an injected password must not be empty");
    }
}
