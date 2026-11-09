package net.onelitefeather.menura.server.publish;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.publish.PostStore;
import net.onelitefeather.menura.core.publish.QuotaLedger;
import net.onelitefeather.menura.core.publish.support.InMemoryPostStore;
import net.onelitefeather.menura.core.publish.support.InMemoryQuotaUsage;

/**
 * Test-only replacement of the post store: rows live in memory, so no test needs a database. The tests inject the same
 * instance to register finished assets and to read back what a request stored.
 */
@Factory
@Requires(env = Environment.TEST)
class PublishTestFakes {

    @Singleton
    InMemoryPostStore inMemoryPostStore() {
        return new InMemoryPostStore();
    }

    @Singleton
    @Replaces(factory = PublishBeans.class, bean = PostStore.class)
    PostStore postStore(InMemoryPostStore store) {
        return store;
    }

    @Singleton
    @Replaces(factory = PublishBeans.class, bean = QuotaLedger.Store.class)
    QuotaLedger.Store quotaStore() {
        return new InMemoryQuotaUsage();
    }
}
