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
import net.onelitefeather.menura.core.publish.PostUpload;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real bodies of the post, plan and calendar endpoints, checked against the contract. A post with its optional values
 * empty and a post with every optional value set both conform.
 */
@MicronautTest(rebuildContext = true)
class PublishResponseContractTest {

    private static final String EDITOR = "editor-contract";
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000013");
    private static final UUID VIDEO = UUID.fromString("a55e7000-0000-4000-8000-000000000013");
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryChannelStore channels;

    @Inject
    InMemoryPostStore posts;

    private final ResponseContract contract = ResponseContract.load();

    @BeforeEach
    void resetFakes() {
        channels.clear();
        posts.clear();
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-contract", "Contract channel",
                ConnectionState.CONNECTED, null, Set.of(), Set.of(), null, NOW, NOW, null));
        posts.registerAsset(VIDEO, "video");
    }

    @Test
    void aDraftWithOnlyTheRequiredFieldsConformsToThePostSchema() throws Exception {
        JsonNode created = send(HttpRequest.POST("/posts", Map.of("channelId", CHANNEL.toString(), "assetId",
                VIDEO.toString(), "title", "Bare draft")));

        assertConforms("Post", created);
    }

    @Test
    void aPostWithEveryOptionalValueConformsToThePostSchema() throws Exception {
        JsonNode created = send(HttpRequest.POST("/posts", Map.of("channelId", CHANNEL.toString(), "assetId",
                VIDEO.toString(), "title", "Full draft", "description", "Text", "hashtags", List.of("gaming"),
                "visibility", "unlisted", "scheduledAt", "2027-01-18T17:00:00Z")));

        assertConforms("Post", send(HttpRequest.GET("/posts/" + created.get("id").asText())));
    }

    @Test
    void aYouTubePostShowsItsDeclarationAndTheAppliedVisibilityAndConforms() throws Exception {
        JsonNode created = send(HttpRequest.POST("/posts", Map.of("channelId", CHANNEL.toString(), "assetId",
                VIDEO.toString(), "title", "Declared", "visibility", "unlisted", "scheduledAt", "2027-01-18T17:00:00Z")));
        String id = created.get("id").asText();
        send(HttpRequest.PUT("/posts/" + id + "/youtube-declaration", Map.of("madeForKids", false, "planShort", false)));
        posts.putUpload(null, UUID.fromString(id), new PostUpload(null, 1, 1, "private",
                "The platform applies private instead of unlisted."), NOW);

        JsonNode shown = send(HttpRequest.GET("/posts/" + id));

        assertConforms("Post", shown);
        assertEquals(false, shown.get("madeForKids").asBoolean(), "the made-for-kids answer is shown");
        assertEquals(true, shown.get("visibilityDeclared").asBoolean(), "the chosen visibility is shown as declared");
        assertEquals(false, shown.get("plannedShort").asBoolean(), "the Short plan is shown");
        assertEquals("private", shown.get("appliedVisibility").asText(), "the visibility applied by the platform is shown");
        assertEquals("The platform applies private instead of unlisted.", shown.get("visibilityProblem").asText(),
                "the mismatch is shown as a problem");
    }

    @Test
    void theRemainingDailyQuotaOfAYouTubeChannelConformsToTheContract() throws Exception {
        JsonNode quota = send(HttpRequest.GET("/channels/" + CHANNEL + "/quota"));

        assertConforms("ChannelQuota", quota);
        assertEquals(10_000, quota.get("unitsRemaining").asInt(), "a channel that used no quota has all units left");
        assertEquals(100, quota.get("uploadsRemaining").asInt(), "a channel that used no quota has all uploads left");
    }

    @Test
    void theCalendarPageAndEveryItemConformToTheContract() throws Exception {
        send(HttpRequest.POST("/posts", Map.of("channelId", CHANNEL.toString(), "assetId", VIDEO.toString(), "title",
                "Calendar item", "scheduledAt", "2026-11-09T17:00:00Z")));

        JsonNode page = send(HttpRequest.GET("/calendar?view=week&date=2026-11-09"));

        assertConforms("CalendarPage", page);
        assertTrue(page.get("items").size() == 1, "the planned post is on the calendar");
        assertConforms("CalendarItem", page.get("items").get(0));
    }

    @Test
    void aPlanResultConformsToTheContract() throws Exception {
        JsonNode plan = send(HttpRequest.POST("/post-plans", Map.of("assetId", VIDEO.toString(), "channels", List.of(
                Map.of("channelId", CHANNEL.toString(), "scheduledAt", "2027-01-18T17:00:00Z", "title", "Planned"),
                Map.of("channelId", UUID.randomUUID().toString(), "title", "Refused")))));

        assertConforms("PostPlanResult", plan);
    }

    private void assertConforms(String schema, JsonNode body) {
        assertTrue(contract.violations(schema, body).isEmpty(),
                schema + " does not match the contract: " + contract.violations(schema, body) + " in " + body);
    }

    private JsonNode send(MutableHttpRequest<?> request) throws Exception {
        HttpResponse<String> response = client.toBlocking().exchange(request
                .header(HeaderIdentityFilter.PERSON_HEADER, EDITOR)
                .header(HeaderIdentityFilter.ROLES_HEADER, "EDITOR"), String.class);
        return JSON.readTree(response.body());
    }
}
