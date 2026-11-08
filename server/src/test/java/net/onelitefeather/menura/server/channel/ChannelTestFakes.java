package net.onelitefeather.menura.server.channel;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.channel.support.ScriptedPlatform;

/**
 * Test-only replacements for the channel beans: rows live in memory and the platform is a scripted endpoint, so no
 * test reaches a database or Google. The tests inject the same instances to script and inspect them.
 */
@Factory
@Requires(env = Environment.TEST)
class ChannelTestFakes {

    @Singleton
    InMemoryChannelStore inMemoryChannelStore() {
        return new InMemoryChannelStore();
    }

    @Singleton
    ScriptedPlatform scriptedPlatform() {
        return new ScriptedPlatform();
    }

    @Singleton
    @Replaces(factory = ChannelBeans.class, bean = ChannelStore.class)
    ChannelStore channelStore(InMemoryChannelStore store) {
        return store;
    }

    @Singleton
    @Replaces(factory = ChannelBeans.class, bean = HttpTransport.class)
    HttpTransport httpTransport(ScriptedPlatform platform) {
        return platform;
    }
}
