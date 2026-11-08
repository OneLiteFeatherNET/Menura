package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.FakePlatform;
import net.onelitefeather.menura.core.channel.support.ScriptedPlatform;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisconnectTest {

    private static final Instant SOON = ChannelFixture.NOW.plus(Duration.ofHours(1));

    @Test
    void disconnectRequestsRevocationAndDeletesTheStoredTokens() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCd1", SOON, "1//to-revoke");
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));

        String revocation = world.service.disconnect(ChannelFixture.ACTOR, channel);

        assertEquals("revoked", revocation);
        assertTrue(world.http.last("/revoke").orElseThrow().formBody().contains("token=1%2F%2Fto-revoke"),
                "the platform is asked to revoke the refresh token");
        assertTrue(world.store.storedGrant(channel).isEmpty(), "the stored tokens are deleted");
        Channel stored = world.store.storedChannel(channel).orElseThrow();
        assertEquals(ConnectionState.DISCONNECTED, stored.state());
        assertEquals("channel.disconnect", world.audit.entries().get(0).action(), "the disconnect is audited");
    }

    @Test
    void tokensAreDeletedEvenWhenTheRevocationFails() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCd2", SOON, "1//stays-at-platform");
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(503, ""));

        String revocation = world.service.disconnect(ChannelFixture.ACTOR, channel);

        assertTrue(world.store.storedGrant(channel).isEmpty(), "a failed revocation must not keep a working token in Menura");
        assertEquals("failed", revocation);
        assertEquals("failed", world.store.storedChannel(channel).orElseThrow().grantRevocation(),
                "the record shows that the platform grant may still exist");
    }

    @Test
    void networkFailureDuringRevocationStillDeletesTheTokens() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCd3", SOON, "1//r");
        world.http.unreachable("POST", "/revoke", new IOException("timeout"));

        world.service.disconnect(ChannelFixture.ACTOR, channel);

        assertTrue(world.store.storedGrant(channel).isEmpty());
    }

    @Test
    void platformWithoutRevocationRecordsThatItWasNotSupported() throws Exception {
        InMemoryChannelStore store = new InMemoryChannelStore();
        FakePlatform platform = new FakePlatform(new PlatformCapabilities(false, false, false, false, false),
                List.of(), Map.of());
        ChannelFixture world = new ChannelFixture();
        ConnectionService service = world.service(store, new PlatformRegistry(List.of(platform)));
        UUID channel = seedFake(store, "fake", "acc");

        String revocation = service.disconnect(ChannelFixture.ACTOR, channel);

        assertEquals("not_supported", revocation, "a platform without revocation is recorded as such");
        assertTrue(store.storedGrant(channel).isEmpty(), "the tokens are deleted anyway");
        assertEquals(ConnectionState.DISCONNECTED, store.storedChannel(channel).orElseThrow().state());
        assertEquals("channel.disconnect", world.audit.entries().get(0).action(), "the disconnect is audited");
    }

    @Test
    void disconnectingTwiceIsRefused() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCd4", SOON, "1//r");
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));
        world.service.disconnect(ChannelFixture.ACTOR, channel);

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.disconnect(ChannelFixture.ACTOR, channel));

        assertEquals(409, failure.status());
        assertNotNull(failure.detail());
    }

    @Test
    void disconnectedChannelKeepsItsIdentityForHistory() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCd5", SOON, "1//r");
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));

        world.service.disconnect(ChannelFixture.ACTOR, channel);

        Channel stored = world.store.storedChannel(channel).orElseThrow();
        assertEquals("UCd5", stored.platformAccountId(), "the channel record stays for its posts and history");
        assertNotNull(stored.disconnectedAt());
    }

    private static UUID seedFake(InMemoryChannelStore store, String platform, String account) {
        UUID id = UUID.nameUUIDFromBytes((platform + account).getBytes(StandardCharsets.UTF_8));
        store.insertChannel(null, new Channel(id, platform, account, "Fake", ConnectionState.CONNECTED, null,
                Set.of(), Set.of(), null, ChannelFixture.NOW, ChannelFixture.NOW, null));
        store.saveGrant(null, new ChannelGrant(id, "tok", "ref", SOON, ChannelFixture.NOW));
        return id;
    }
}
