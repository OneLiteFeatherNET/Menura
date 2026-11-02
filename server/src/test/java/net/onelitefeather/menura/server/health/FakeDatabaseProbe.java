package net.onelitefeather.menura.server.health;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/** In-memory replacement for the JDBC probe, so health tests never open a connection. */
@Singleton
@Replaces(DataSourceProbe.class)
class FakeDatabaseProbe implements DatabaseProbe {

    private final boolean reachable;

    FakeDatabaseProbe(@Value("${test.database.reachable:true}") boolean reachable) {
        this.reachable = reachable;
    }

    @Override
    public boolean isReachable() {
        return reachable;
    }
}
