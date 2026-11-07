package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostsTest {

    private final List<Model.Channel> channels = DemoSeed.channels();

    @Test
    void normalizeTrimsLowerCasesAndDropsTheTrailingDotAndThePort() {
        assertEquals("yt.onelitefeather.net", Hosts.normalize(" YT.OneLiteFeather.net.:443 "));
    }

    @Test
    void aKnownHostMapsToItsChannel() {
        assertEquals(Optional.of("tt-de"), Hosts.channelIdFor(channels, "tt.onelitefeather.net"));
    }

    @Test
    void aHostWithUpperCaseTrailingDotAndPortStillMapsToItsChannel() {
        assertEquals(Optional.of("yt-de"), Hosts.channelIdFor(channels, "YT.onelitefeather.net.:443"));
    }

    @Test
    void thePlainDomainIsNotAChannelSoItCountsAsDirect() {
        assertEquals(Optional.empty(), Hosts.channelIdFor(channels, "onelitefeather.net"));
    }

    @Test
    void anUnknownHostIsNotAChannelSoItCountsAsDirect() {
        assertEquals(Optional.empty(), Hosts.channelIdFor(channels, "unknown.example"));
    }
}
