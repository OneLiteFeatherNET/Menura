package net.onelitefeather.menura.core.post.support;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.DeterministicRandom;
import net.onelitefeather.menura.core.channel.support.DirectTransactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPostService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One fresh world per test: the channel fixture (clock, store, randomness), a listing platform, the external post store
 * and the service over them. No test shares any of it with another.
 */
public final class PostFixture {

    public final ChannelFixture channels = new ChannelFixture();
    public final ListingPlatform platform = new ListingPlatform();
    public final InMemoryExternalPostStore posts = new InMemoryExternalPostStore();
    public final PlatformRegistry registry = new PlatformRegistry(List.of(platform));
    public final ConnectionService connections = channels.service(channels.store, registry);
    public final ExternalPostService service;

    public PostFixture() {
        this(ExternalPostService.LISTING_LIMIT);
    }

    public PostFixture(int listingLimit) {
        this.service = new ExternalPostService(new DirectTransactions(), channels.clock,
                new IdentifierGenerator(new DeterministicRandom(7L)), channels.store, posts, connections, registry,
                listingLimit);
    }

    /** Stores a connected channel of the listing platform with a grant that does not expire during the test. */
    public UUID connectedChannel(String account) {
        return channel(account, ConnectionState.CONNECTED);
    }

    /** Stores a channel of the listing platform in the given state. */
    public UUID channel(String account, ConnectionState state) {
        UUID id = UUID.nameUUIDFromBytes(("listing-" + account).getBytes(StandardCharsets.UTF_8));
        String reason = state == ConnectionState.NEEDS_RECONNECTION ? "The grant was rejected." : null;
        channels.store.insertChannel(null, new Channel(id, ListingPlatform.PLATFORM, account, "Channel " + account,
                state, reason, Set.of(), Set.of("listing.account"), null, ChannelFixture.NOW, ChannelFixture.NOW,
                null));
        channels.store.saveGrant(null, new ChannelGrant(id, "token-" + account, null,
                ChannelFixture.NOW.plus(Duration.ofDays(365)), ChannelFixture.NOW));
        return id;
    }
}
