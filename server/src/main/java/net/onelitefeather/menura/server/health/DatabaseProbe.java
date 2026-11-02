package net.onelitefeather.menura.server.health;

/** Readiness of the PostgreSQL database that the server depends on. */
public interface DatabaseProbe {

    boolean isReachable();
}
