package net.onelitefeather.menura.server.publish;

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
import net.onelitefeather.menura.core.publish.support.InMemoryPostStore;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calendar feed with planned posts over HTTP: a publisher's token lists the approvals due, an editor's token does
 * not, and a write request to the feed changes nothing.
 */
@MicronautTest(rebuildContext = true)
class CalendarFeedContentHttpTest {

    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000012");
    private static final UUID VIDEO = UUID.fromString("a55e7000-0000-4000-8000-000000000012");
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final String SLOT = "2027-01-18T17:00:00Z";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryChannelStore channels;

    @Inject
    InMemoryPostStore posts;

    @BeforeEach
    void resetFakes() {
        channels.clear();
        posts.clear();
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-feed", "Feed channel", ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));
        posts.registerAsset(VIDEO, "video");
    }

    @Test
    void aPublisherFeedListsTheApprovalDueAndThePlannedPost() throws Exception {
        String token = issue("publisher-feed", "PUBLISHER");
        submitInReview();

        String feed = client.toBlocking().retrieve(HttpRequest.GET("/calendar/feed/" + token));

        assertTrue(feed.startsWith("BEGIN:VCALENDAR"), "the feed is an iCalendar document");
        assertTrue(feed.contains("SUMMARY:Planned: Feed clip"), "the planned post is an event");
        assertTrue(feed.contains("SUMMARY:Approval due: Feed clip"), "a publisher sees the approval due");
    }

    @Test
    void anEditorFeedListsThePlannedPostButNoApproval() throws Exception {
        String token = issue("editor-feed", "EDITOR");
        submitInReview();

        String feed = client.toBlocking().retrieve(HttpRequest.GET("/calendar/feed/" + token));

        assertTrue(feed.contains("SUMMARY:Planned: Feed clip"), "the planned post is listed");
        assertFalse(feed.contains("Approval due"), "an editor who is not a publisher sees no approval");
    }

    @Test
    void aWriteRequestToTheFeedIsRefusedAndChangesNothing() throws Exception {
        String token = issue("editor-write", "EDITOR");
        submitInReview();
        int before = posts.all().size();

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/calendar/feed/" + token, "{}"), String.class));

        assertTrue(failure.getStatus().getCode() >= 400, "the feed has no write route");
        assertEquals(before, posts.all().size(), "no post changed through the feed");
    }

    @Test
    void anUnknownFeedTokenAnswersNotFound() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/calendar/feed/" + UUID.randomUUID() + "."
                        + "Q".repeat(43)), String.class));

        assertEquals(404, failure.getStatus().getCode(), "an unknown token is Not Found");
    }

    private String issue(String person, String roles) {
        HttpResponse<Map> response = client.toBlocking().exchange(withIdentity(HttpRequest.POST("/calendar-feed-token", ""),
                person, roles), Map.class);
        return (String) response.getBody(Map.class).orElseThrow().get("token");
    }

    private void submitInReview() throws Exception {
        Map<?, ?> created = client.toBlocking().exchange(withIdentity(HttpRequest.POST("/posts", Map.of("channelId",
                CHANNEL.toString(), "assetId", VIDEO.toString(), "title", "Feed clip", "scheduledAt", SLOT)),
                "editor-feed", "EDITOR"), Map.class).getBody(Map.class).orElseThrow();
        client.toBlocking().exchange(withIdentity(HttpRequest.POST("/posts/" + created.get("id") + "/submission", Map.of()),
                "editor-feed", "EDITOR"), Map.class);
    }

    private static MutableHttpRequest<?> withIdentity(MutableHttpRequest<?> request, String person, String roles) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person).header(HeaderIdentityFilter.ROLES_HEADER, roles);
    }
}
