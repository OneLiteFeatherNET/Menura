package net.onelitefeather.menura.server.post;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The external posts of a channel over HTTP, with the rows in memory. Each test clears the fakes first, so no test
 * depends on another.
 */
@MicronautTest(rebuildContext = true)
class ExternalPostHttpTest {

    private static final String VIEWER = "viewer-1";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000001");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryChannelStore channels;

    @Inject
    InMemoryExternalPostStore posts;

    @BeforeEach
    void resetFakes() {
        channels.clear();
        posts.clear();
    }

    @Test
    void aViewerSeesEachPostWithItsStatusAndNoEditControl() throws Exception {
        seedChannel(ConnectionState.CONNECTED);
        seedPost("live-1", "Stream archive", NOW.minus(Duration.ofDays(2)), null);

        Map<?, ?> page = get(VIEWER, "/channels/" + CHANNEL + "/external-posts");

        assertEquals("connected", page.get("channelState"), "the channel state is shown");
        List<?> items = (List<?>) page.get("items");
        assertEquals(1, items.size(), "the one post is listed");
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals("Stream archive", item.get("title"), "the title is listed");
        assertEquals("video", item.get("format"), "the format is listed");
        assertEquals("live", item.get("status"), "a live post has the live status");
        assertEquals("https://video.example.test/live-1", item.get("url"), "a live post offers its link");
        assertEquals(NOW.minus(Duration.ofDays(2)).toString(), item.get("publishedAt"), "the publish time is UTC");
    }

    @Test
    void aRemovedPostIsListedAsRemovedWithoutALink() throws Exception {
        seedChannel(ConnectionState.CONNECTED);
        seedPost("gone-1", "Deleted on the platform", NOW.minus(Duration.ofDays(9)), NOW.minus(Duration.ofDays(1)));

        Map<?, ?> page = get(VIEWER, "/channels/" + CHANNEL + "/external-posts");

        Map<?, ?> item = (Map<?, ?>) ((List<?>) page.get("items")).get(0);
        assertEquals("removed", item.get("status"), "the removed post is listed as removed");
        assertNull(item.get("url"), "a removed post offers no link");
        assertTrue(item.get("removedAt") != null, "the removal time is shown");
    }

    @Test
    void aChannelWaitingForReconnectionKeepsItsPostsAndSaysSo() throws Exception {
        seedChannel(ConnectionState.NEEDS_RECONNECTION);
        seedPost("kept-1", "Before the outage", NOW.minus(Duration.ofDays(3)), null);

        Map<?, ?> page = get(VIEWER, "/channels/" + CHANNEL + "/external-posts");

        assertEquals("needs_reconnection", page.get("channelState"), "the waiting channel says so");
        assertEquals(1, ((List<?>) page.get("items")).size(), "its posts are kept and listed");
    }

    @Test
    void theListingPagesNewestFirstWithACursor() throws Exception {
        seedChannel(ConnectionState.CONNECTED);
        seedPost("older", "Older", NOW.minus(Duration.ofDays(5)), null);
        seedPost("newer", "Newer", NOW.minus(Duration.ofDays(1)), null);

        Map<?, ?> first = get(VIEWER, "/channels/" + CHANNEL + "/external-posts?limit=1");
        Map<?, ?> second = get(VIEWER, "/channels/" + CHANNEL + "/external-posts?limit=1&cursor="
                + first.get("nextCursor"));

        assertEquals("Newer", ((Map<?, ?>) ((List<?>) first.get("items")).get(0)).get("title"), "newest first");
        assertEquals("Older", ((Map<?, ?>) ((List<?>) second.get("items")).get(0)).get("title"), "then the older one");
        assertNull(second.get("nextCursor"), "the last page has no next cursor");
    }

    @Test
    void aChannelThatDoesNotExistAnswersNotFoundAsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> get(VIEWER, "/channels/" + UUID.randomUUID() + "/external-posts"));

        assertEquals(404, failure.getStatus().getCode(), "a missing channel is Not Found");
        assertEquals("application/problem+json", failure.getResponse().header("Content-Type"),
                "the answer is Problem Details");
    }

    @Test
    void anAnonymousCallerIsRefused() {
        seedChannel(ConnectionState.CONNECTED);

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/channels/" + CHANNEL + "/external-posts"), Map.class));

        assertEquals(401, failure.getStatus().getCode(), "only a signed-in person reads the posts");
    }

    private void seedChannel(ConnectionState state) {
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-test", "Test channel", state,
                state == ConnectionState.NEEDS_RECONNECTION ? "The grant was rejected." : null, Set.of(), Set.of(), null,
                NOW, NOW, null));
    }

    private void seedPost(String item, String title, Instant publishedAt, Instant removedAt) throws Exception {
        ExternalPost post = new ExternalPost(UUID.nameUUIDFromBytes(item.getBytes()), CHANNEL, "youtube", item,
                "https://video.example.test/" + item, title, "video", publishedAt, removedAt, NOW, NOW);
        posts.insert(null, post);
    }

    private Map<?, ?> get(String person, String uri) {
        MutableHttpRequest<?> request = HttpRequest.GET(uri)
                .header(HeaderIdentityFilter.PERSON_HEADER, person)
                .header(HeaderIdentityFilter.ROLES_HEADER, "VIEWER");
        HttpResponse<Map> response = client.toBlocking().exchange(request, Map.class);
        return response.body();
    }
}
