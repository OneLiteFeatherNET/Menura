package net.onelitefeather.menura.server.channel;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.channel.support.ScriptedPlatform;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The channel endpoints over HTTP, with the rows in memory and the platform scripted. Each test clears both first, so
 * no test depends on another.
 */
@MicronautTest(rebuildContext = true)
class ChannelHttpTest {

    private static final String PUBLISHER = "publisher-1";
    private static final String VIEWER = "viewer-1";
    private static final String ADMIN = "admin-1";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final String READONLY = "https://www.googleapis.com/auth/youtube.readonly";
    private static final String UPLOAD = "https://www.googleapis.com/auth/youtube.upload";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryChannelStore store;

    @Inject
    ScriptedPlatform platform;

    @BeforeEach
    void resetFakes() {
        store.clear();
        platform.clear();
    }

    @Test
    void publisherStartsAConnectionAndReceivesThePlatformConsentLink() {
        HttpResponse<Map> response = client.toBlocking().exchange(as(PUBLISHER, HttpRequest.POST("/channels/connections",
                Map.of("platform", "youtube", "features", List.of("publish")))), Map.class);

        assertEquals(202, response.code(), "the consent link is returned as accepted, the browser follows it");
        String url = (String) response.body().get("authorizationUrl");
        assertTrue(url.startsWith("https://accounts.google.com/"), "the link is the platform's own consent screen");
        assertEquals("S256", query(url).get("code_challenge_method"), "the consent carries a PKCE challenge");
        assertEquals("no-store", response.header("Cache-Control"), "the consent link is never cached");
    }

    @Test
    void viewerCannotStartAConnection() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(as(VIEWER, HttpRequest.POST("/channels/connections",
                        Map.of("platform", "youtube", "features", List.of("publish"))))));

        assertProblem(failure, 403);
    }

    @Test
    void anonymousRequestCannotListChannels() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/channels"), Map.class));

        assertProblem(failure, 401);
    }

    @Test
    void refusedConsentRedirectsToTheChannelPageAndCreatesNoChannel() {
        String state = startConsent(PUBLISHER);

        HttpResponse<Void> callback = client.toBlocking().exchange(as(PUBLISHER,
                HttpRequest.GET("/channel-connections/callback?state=" + state + "&error=access_denied")));

        assertEquals(302, callback.code());
        String location = callback.header("Location");
        assertTrue(location.startsWith("http://localhost:5173/channels?connection=refused"),
                "the browser returns to the channel page with the outcome");
        assertTrue(location.contains("reason="), "the refusal reason is passed to the page");
        assertTrue(store.allChannels().isEmpty(), "a refused consent creates no channel");
    }

    @Test
    void completedConsentCreatesAChannelThatTheApiReturnsWithoutAnyToken() {
        scriptConsent("ya29.SECRET-ACCESS", "1//SECRET-REFRESH", READONLY + " " + UPLOAD, "UCmain", "Main channel");
        String state = startConsent(PUBLISHER);

        HttpResponse<Void> callback = client.toBlocking().exchange(as(PUBLISHER,
                HttpRequest.GET("/channel-connections/callback?state=" + state + "&code=auth-code")));

        assertTrue(callback.header("Location").contains("connection=connected&channel="),
                "a completed consent reports the connected channel");
        HttpResponse<String> list = client.toBlocking().exchange(as(VIEWER, HttpRequest.GET("/channels")), String.class);
        String body = list.body();
        assertTrue(body.contains("Main channel"), "the channel is listed");
        assertTrue(body.contains("\"state\":\"connected\""), "the channel is connected");
        assertFalse(body.contains("SECRET"), "no token value reaches a response");
        assertFalse(body.toLowerCase().contains("accesstoken") || body.toLowerCase().contains("refreshtoken"),
                "no token field exists in the channel representation");
    }

    @Test
    void disconnectRevokesAtThePlatformAndAnswersTheRevocation() {
        UUID channel = seedConnectedChannel();
        platform.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));

        HttpResponse<Map> response = client.toBlocking().exchange(as(PUBLISHER,
                HttpRequest.DELETE("/channels/" + channel)), Map.class);

        assertEquals(200, response.code());
        assertEquals("revoked", response.body().get("revocation"), "the revocation outcome is reported");
        assertEquals(ConnectionState.DISCONNECTED, store.storedChannel(channel).orElseThrow().state());
    }

    @Test
    void viewerCannotDisconnectAChannel() {
        UUID channel = seedConnectedChannel();

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(as(VIEWER, HttpRequest.DELETE("/channels/" + channel))));

        assertProblem(failure, 403);
        assertEquals(ConnectionState.CONNECTED, store.storedChannel(channel).orElseThrow().state(),
                "a refused request changes nothing");
    }

    @Test
    void onlyAnAdministratorRecordsTheAuditStatus() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(as(PUBLISHER, HttpRequest.PUT("/platforms/youtube/audit-status",
                        Map.of("status", "audited", "source", "Google letter")))));

        assertProblem(failure, 403);
    }

    @Test
    void recordedUnauditedStatusShowsItsTextAndRestriction() {
        HttpResponse<Map> put = client.toBlocking().exchange(as(ADMIN, HttpRequest.PUT("/platforms/youtube/audit-status",
                Map.of("status", "unaudited", "source", "API console review"))), Map.class);
        assertEquals(200, put.code());

        HttpResponse<String> platforms = client.toBlocking().exchange(as(VIEWER, HttpRequest.GET("/platforms")), String.class);

        String body = platforms.body();
        assertTrue(body.contains("unaudited: uploads private only"), "the channel view shows the audit text");
        assertTrue(body.contains("public visibility not available"), "the restriction is readable");
    }

    @Test
    void unknownChannelAnswers404AsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(as(VIEWER, HttpRequest.GET("/channels/" + UUID.randomUUID())), Map.class));

        assertProblem(failure, 404);
    }

    @Test
    void listingPagesWithACursorAndNeverRepeatsAChannel() {
        for (int i = 1; i <= 3; i++) {
            seedChannel("UC" + i, "Channel " + i);
        }

        HttpResponse<Map> first = client.toBlocking().exchange(as(VIEWER, HttpRequest.GET("/channels?limit=2")), Map.class);
        String cursor = (String) first.body().get("nextCursor");
        HttpResponse<Map> second = client.toBlocking().exchange(as(VIEWER,
                HttpRequest.GET("/channels?limit=2&cursor=" + cursor)), Map.class);

        assertEquals(2, ((List<?>) first.body().get("items")).size());
        assertNotNull(cursor, "a page that is not the last carries a cursor");
        assertEquals(1, ((List<?>) second.body().get("items")).size(), "the rest is on the next page");
        assertEquals(null, second.body().get("nextCursor"), "the last page has no cursor");
    }

    @Test
    void invalidPlatformNameIsRefusedAsProblemDetails() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(as(PUBLISHER, HttpRequest.POST("/channels/connections",
                        Map.of("platform", "Not A Platform!")))));

        assertProblem(failure, 400);
    }

    // ----------------------------------------------------------- helpers

    private String startConsent(String person) {
        HttpResponse<Map> response = client.toBlocking().exchange(as(person, HttpRequest.POST("/channels/connections",
                Map.of("platform", "youtube", "features", List.of("publish")))), Map.class);
        return query((String) response.body().get("authorizationUrl")).get("state");
    }

    private void scriptConsent(String access, String refresh, String scope, String account, String title) {
        platform.route("POST", "/token", new ScriptedPlatform.Reply(200,
                "{\"access_token\":\"" + access + "\",\"expires_in\":3600,\"refresh_token\":\"" + refresh
                        + "\",\"scope\":\"" + scope + "\"}"));
        platform.route("GET", "/youtube/v3/channels", new ScriptedPlatform.Reply(200,
                "{\"items\":[{\"id\":\"" + account + "\",\"snippet\":{\"title\":\"" + title + "\"}}]}"));
    }

    private UUID seedConnectedChannel() {
        return seedChannel("UCconnected", "Connected channel");
    }

    private UUID seedChannel(String account, String title) {
        UUID id = UUID.nameUUIDFromBytes(account.getBytes(StandardCharsets.UTF_8));
        store.insertChannel(null, new Channel(id, "youtube", account, title, ConnectionState.CONNECTED, null,
                Set.of("publish"), Set.of(READONLY, UPLOAD), null, NOW, NOW, null));
        store.saveGrant(null, new ChannelGrant(id, "ya29.seeded", "1//seeded", NOW.plusSeconds(3600), NOW));
        return id;
    }

    private static MutableHttpRequest<?> as(String person, MutableHttpRequest<?> request) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person)
                .header(HeaderIdentityFilter.ROLES_HEADER, rolesOf(person));
    }

    private static String rolesOf(String person) {
        return switch (person) {
            case PUBLISHER -> "PUBLISHER";
            case ADMIN -> "ADMIN";
            default -> "VIEWER";
        };
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new java.util.HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static void assertProblem(HttpClientResponseException failure, int status) {
        assertEquals(status, failure.getStatus().getCode());
        assertEquals("application/problem+json", failure.getResponse().getContentType().orElseThrow().toString());
    }
}
