package net.onelitefeather.menura.server.post;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.post.support.InMemoryExternalPostStore;

/**
 * Test-only replacement of the external post store: rows live in memory, so no test needs a database. The tests inject
 * the same instance to seed the posts they read back over HTTP.
 */
@Factory
@Requires(env = Environment.TEST)
class ExternalPostTestFakes {

    @Singleton
    InMemoryExternalPostStore inMemoryExternalPostStore() {
        return new InMemoryExternalPostStore();
    }

    @Singleton
    @Replaces(factory = ExternalPostBeans.class, bean = ExternalPostStore.class)
    ExternalPostStore externalPostStore(InMemoryExternalPostStore store) {
        return store;
    }
}
