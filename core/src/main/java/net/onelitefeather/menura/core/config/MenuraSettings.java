package net.onelitefeather.menura.core.config;

import java.util.Map;
import java.util.Objects;

/**
 * Settings shared by the server and the jobs. Each value is read from an environment variable and falls back
 * to a local-development default that points at the docker-compose services.
 */
public record MenuraSettings(String databaseUrl, String databaseUser, String databasePassword) {

    public static final String DATABASE_URL_VARIABLE = "MENURA_DB_URL";
    public static final String DATABASE_USER_VARIABLE = "MENURA_DB_USER";
    public static final String DATABASE_PASSWORD_VARIABLE = "MENURA_DB_PASSWORD";

    static final String DEFAULT_DATABASE_URL = "jdbc:postgresql://localhost:5432/menura";
    static final String DEFAULT_DATABASE_USER = "menura";
    static final String DEFAULT_DATABASE_PASSWORD = "menura";

    public MenuraSettings {
        Objects.requireNonNull(databaseUrl, "databaseUrl");
        Objects.requireNonNull(databaseUser, "databaseUser");
        Objects.requireNonNull(databasePassword, "databasePassword");
    }

    /** Reads the settings from an environment map, normally {@code System.getenv()}. */
    public static MenuraSettings from(Map<String, String> environment) {
        return new MenuraSettings(
                environment.getOrDefault(DATABASE_URL_VARIABLE, DEFAULT_DATABASE_URL),
                environment.getOrDefault(DATABASE_USER_VARIABLE, DEFAULT_DATABASE_USER),
                environment.getOrDefault(DATABASE_PASSWORD_VARIABLE, DEFAULT_DATABASE_PASSWORD));
    }
}
