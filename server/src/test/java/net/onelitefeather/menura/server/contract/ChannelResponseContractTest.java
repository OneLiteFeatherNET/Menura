package net.onelitefeather.menura.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.support.InMemoryExternalPostStore;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real bodies of the channel, platform and external-post endpoints, for a channel whose optional values are null
 * or empty, checked against the contract. The rows are in memory and each test clears them first.
 */
@MicronautTest(rebuildContext = true)
class ChannelResponseContractTest {

    private static final String VIEWER = "viewer-1";
    private static final String ADMIN = "admin-1";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000002");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryChannelStore channels;

    @Inject
    InMemoryExternalPostStore posts;

    private final ResponseContract contract = ResponseContract.load();

    @BeforeEach
    void resetFakes() {
        channels.clear();
        posts.clear();
    }

    @Test
    void channelPageConformsWhenTheChannelHasNoScopesFeaturesOrReason() throws Exception {
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-empty", "Empty channel", ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));

        JsonNode page = get("/channels");

        assertConforms("ChannelPage", page);
    }

    @Test
    void platformsConformWhenTheAuditPassedAndHasNoRestrictions() throws Exception {
        MutableHttpRequest<?> audit = HttpRequest.PUT("/platforms/youtube/audit-status",
                        Map.of("status", "audited", "source", "Google letter"))
                .header(HeaderIdentityFilter.PERSON_HEADER, ADMIN)
                .header(HeaderIdentityFilter.ROLES_HEADER, "ADMIN");
        client.toBlocking().exchange(audit, Map.class);

        JsonNode platforms = get("/platforms");

        assertTrue(platforms.isArray() && !platforms.isEmpty(), "the platforms of this build are listed");
        for (JsonNode platform : platforms) {
            assertConforms("Platform", platform);
        }
    }

    @Test
    void externalPostPageConformsWhenNothingWasSyncedAndNoPostExists() throws Exception {
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-quiet", "Quiet channel", ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));

        JsonNode page = get("/channels/" + CHANNEL + "/external-posts");

        assertConforms("ExternalPostPage", page);
    }

    @Test
    void externalPostPageConformsWhenAPostIsRemovedAndHasNoLink() throws Exception {
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-removed", "Removed channel", ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));
        posts.insert(null, new ExternalPost(UUID.nameUUIDFromBytes("gone".getBytes()), CHANNEL, "youtube", "gone",
                "https://video.example.test/gone", "Deleted on the platform", "video", NOW.minus(Duration.ofDays(9)),
                NOW.minus(Duration.ofDays(1)), NOW, NOW));

        JsonNode page = get("/channels/" + CHANNEL + "/external-posts");

        assertConforms("ExternalPostPage", page);
    }

    private void assertConforms(String schema, JsonNode body) {
        assertTrue(contract.violations(schema, body).isEmpty(),
                schema + " does not match the contract: " + contract.violations(schema, body) + " in " + body);
    }

    private JsonNode get(String uri) throws Exception {
        MutableHttpRequest<?> request = HttpRequest.GET(uri)
                .header(HeaderIdentityFilter.PERSON_HEADER, VIEWER)
                .header(HeaderIdentityFilter.ROLES_HEADER, "VIEWER");
        HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
        return JSON.readTree(response.body());
    }
}
