package net.onelitefeather.menura.server.attribution;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.attribution.JoinStore;
import net.onelitefeather.menura.core.attribution.support.InMemoryJoinStore;

/**
 * Test-only replacement of the join store: events, answers, settings and the rollup live in memory, so no test needs a
 * database. The tests inject the same instance to seed posts and to read back what was stored.
 */
@Factory
@Requires(env = Environment.TEST)
class JoinTestFakes {

    @Singleton
    InMemoryJoinStore inMemoryJoinStore() {
        return new InMemoryJoinStore();
    }

    @Singleton
    @Replaces(factory = JoinAttributionBeans.class, bean = JoinStore.class)
    JoinStore joinStore(InMemoryJoinStore store) {
        return store;
    }
}
