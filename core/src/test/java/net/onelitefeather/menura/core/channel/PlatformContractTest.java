package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.FakePlatform;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The adapter port as every platform change sees it, exercised through an in-memory fake adapter. */
class PlatformContractTest {

    private static final Instant SOON = ChannelFixture.NOW.plus(Duration.ofHours(1));
    private static final PlatformCapabilities ALL = new PlatformCapabilities(true, true, true, true, true);
    private static final PlatformCapabilities NO_SCHEDULING = new PlatformCapabilities(true, false, true, true, true);

    @Test
    void everyAdapterExposesTheCommonOperations() throws Exception {
        Set<String> names = Arrays.stream(PlatformAdapter.class.getMethods()).map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());

        for (String operation : List.of("publish", "status", "delete", "capabilities", "listPublished", "readMetrics")) {
            assertTrue(names.contains(operation), "every adapter must expose " + operation);
        }
    }

    @Test
    void listingReturnsEachItemWithIdentifierUrlTitleTimeAndFormat() throws Exception {
        PlatformAdapter.PublishedItem item = new PlatformAdapter.PublishedItem("vid-1",
                "https://platform.example.test/vid-1", "Episode 1", ChannelFixture.NOW, "video/mp4");
        Fixture fixture = fixture(ALL, List.of(item), Map.of());

        List<PlatformAdapter.PublishedItem> listed = fixture.service.listPublished(fixture.channel, 50);

        assertEquals(List.of(item), listed, "the item is returned with every field the platform reported");
    }

    @Test
    void listingDropsAnItemWithoutAPlatformIdentifier() throws Exception {
        PlatformAdapter.PublishedItem anonymous = new PlatformAdapter.PublishedItem(" ",
                "https://platform.example.test/x", "No id", ChannelFixture.NOW, "video/mp4");
        PlatformAdapter.PublishedItem real = new PlatformAdapter.PublishedItem("vid-2",
                "https://platform.example.test/vid-2", "Real", ChannelFixture.NOW, "video/mp4");
        Fixture fixture = fixture(ALL, List.of(anonymous, real), Map.of());

        List<PlatformAdapter.PublishedItem> listed = fixture.service.listPublished(fixture.channel, 50);

        assertEquals(List.of(real), listed, "only items the platform identifies are returned");
    }

    @Test
    void listingIsRefusedWhenThePlatformDoesNotSupportIt() throws Exception {
        Fixture fixture = fixture(new PlatformCapabilities(true, true, false, true, true), List.of(), Map.of());

        ProblemException failure = assertThrows(ProblemException.class,
                () -> fixture.service.listPublished(fixture.channel, 50));

        assertEquals(501, failure.status(), "the server answers 501 for an operation the platform does not offer");
    }

    @Test
    void metricThatTheAdapterDoesNotExposeIsAbsentNotZero() throws Exception {
        Fixture fixture = fixture(ALL, List.of(), Map.of("views", 42L));

        Map<String, OptionalLong> readings = fixture.service.readMetrics(fixture.channel, "vid-1", Set.of("views", "likes"));

        assertEquals(OptionalLong.of(42L), readings.get("views"));
        assertEquals(OptionalLong.empty(), readings.get("likes"),
                "a metric the platform does not expose is recorded as unavailable, never as zero");
    }

    @Test
    void metricsAreAllAbsentWhenThePlatformCannotReadThem() throws Exception {
        Fixture fixture = fixture(new PlatformCapabilities(true, true, true, false, true), List.of(), Map.of("views", 9L));

        Map<String, OptionalLong> readings = fixture.service.readMetrics(fixture.channel, "vid-1", Set.of("views"));

        assertEquals(OptionalLong.empty(), readings.get("views"));
    }

    @Test
    void schedulingIsRefusedWithAReasonOnAPlatformWithoutNativeScheduling() throws Exception {
        Fixture fixture = fixture(NO_SCHEDULING, List.of(), Map.of());

        ProblemException failure = assertThrows(ProblemException.class,
                () -> fixture.service.requireScheduling(fixture.channel));

        assertEquals(422, failure.status());
        assertTrue(failure.detail().contains("does not support scheduled publishing"),
                "the refusal names the reason");
    }

    @Test
    void schedulingIsAllowedOnAPlatformWithNativeScheduling() throws Exception {
        Fixture fixture = fixture(ALL, List.of(), Map.of());

        assertDoesNotThrow(() -> fixture.service.requireScheduling(fixture.channel),
                "a platform with native scheduling accepts a future publication time");
    }

    @Test
    void aFeatureMissingItsScopeIsRefusedBeforeUse() throws Exception {
        Fixture fixture = fixture(ALL, List.of(), Map.of());
        fixture.world.store.saveGrant(null, new net.onelitefeather.menura.core.channel.ChannelGrant(
                fixture.channel, "ya29", "1//r", SOON, ChannelFixture.NOW));
        Channel readonlyOnly = new Channel(fixture.channel, "fake", "acc", "Fake", ConnectionState.CONNECTED, null,
                Set.of("publish"), Set.of("fake.account"), null, ChannelFixture.NOW, ChannelFixture.NOW, null);
        fixture.world.store.updateChannel(null, readonlyOnly);

        ProblemException failure = assertThrows(ProblemException.class,
                () -> fixture.service.requireFeature(fixture.channel, "publish"));

        assertEquals(409, failure.status(), "a feature is not used before its scope is granted");
    }

    /** A fake adapter registered next to a seeded channel of its platform. */
    private record Fixture(ChannelFixture world, ConnectionService service, UUID channel) {
    }

    private static Fixture fixture(PlatformCapabilities capabilities, List<PlatformAdapter.PublishedItem> items,
                                   Map<String, Long> metrics) {
        ChannelFixture world = new ChannelFixture();
        InMemoryChannelStore store = world.store;
        FakePlatform platform = new FakePlatform(capabilities, items, metrics);
        ConnectionService service = world.service(store, new PlatformRegistry(List.of(platform)));
        UUID channel = UUID.nameUUIDFromBytes("fake-channel".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        store.insertChannel(null, new Channel(channel, "fake", "acc", "Fake", ConnectionState.CONNECTED, null,
                Set.of("publish"), Set.of("fake.account", "fake.publish"), null, ChannelFixture.NOW, ChannelFixture.NOW, null));
        store.saveGrant(null, new net.onelitefeather.menura.core.channel.ChannelGrant(channel, "ya29", "1//r", SOON, ChannelFixture.NOW));
        return new Fixture(world, service, channel);
    }
}
