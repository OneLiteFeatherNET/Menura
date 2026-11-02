package net.onelitefeather.menura.server.db;

/** Applies the schema migrations from inside the server. Used only where the local profile enables it. */
public interface MigrationRunner {

    void migrate();
}
