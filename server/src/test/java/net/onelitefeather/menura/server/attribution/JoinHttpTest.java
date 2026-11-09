package net.onelitefeather.menura.server.attribution;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.attribution.AttributablePost;
import net.onelitefeather.menura.core.attribution.support.InMemoryJoinStore;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The join endpoints and the admin and report routes over HTTP, with the join store in memory and the test clock. Each test
 * starts from an empty store, and the times it uses are fixed relative to the test clock.
 */
@MicronautTest(rebuildContext = true)
class JoinHttpTest {

    private static final String PROXY = "Bearer test-join-proxy-credential";
    private static final String PSEUDONYM = "e".repeat(64);
    private static final String ADMIN = "admin-1";
    private static final String VIEWER = "viewer-1";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryJoinStore store;

    @Inject
    Clock clock;

    @BeforeEach
    void startFromAnEmptyStore() {
        store.clear();
    }

    @Test
    void theProxyRecordsAFirstJoinAndARepeatOfItAnswersOkWithoutCountingAgain() {
        String body = event(UUID.fromString("7a1f0c2e-3b4d-4e5f-8a6b-7c8d9e0f1a2b"), true, clock.instant());

        Reply created = send(proxy(HttpRequest.POST("/join-events", body)));
        Reply repeated = send(proxy(HttpRequest.POST("/join-events", body)));

        assertEquals(201, created.status(), "a new event is created");
        assertEquals(200, repeated.status(), "the same event again is answered without a new count");
        assertEquals(1, store.eventCount(), "one event is stored");
    }

    @Test
    void aRepeatedEventIdWithOtherContentAnswers409AndKeepsTheStoredEvent() {
        UUID id = UUID.fromString("8b2a1d3f-4c5e-4f60-9b7c-8d9e0f1a2b3c");
        send(proxy(HttpRequest.POST("/join-events", event(id, true, clock.instant()))));

        Reply conflict = send(proxy(HttpRequest.POST("/join-events", event(id, false, clock.instant()))));

        assertEquals(409, conflict.status(), "the same id with another first-join flag is a conflict");
        assertTrue(store.findEvent(null, id).orElseThrow().firstJoin(), "the stored event is unchanged");
    }

    @Test
    void aCallWithoutTheProxyCredentialAnswers401AndStoresNothing() {
        Reply refused = send(HttpRequest.POST("/join-events", event(UUID.randomUUID(), true, clock.instant())));

        assertEquals(401, refused.status(), "a call without a credential is refused");
        assertEquals(0, store.eventCount(), "nothing is stored");
    }

    @Test
    void aSignedInPersonCannotSendJoinEvents() {
        Reply refused = send(HttpRequest.POST("/join-events", event(UUID.randomUUID(), true, clock.instant()))
                .header(HeaderIdentityFilter.PERSON_HEADER, VIEWER)
                .header(HeaderIdentityFilter.ROLES_HEADER, "ADMIN"));

        assertEquals(403, refused.status(), "a person's token is not the proxy's credential");
        assertEquals(0, store.eventCount(), "nothing is stored");
    }

    @Test
    void aPayloadWithAPlayerUuidIsRefusedAndTheUuidIsNotRepeated() {
        String playerUuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"firstJoin\":true,\"joinedAt\":\"" + clock.instant()
                + "\",\"pseudonym\":\"" + PSEUDONYM + "\",\"uuid\":\"" + playerUuid + "\"}";

        Reply refused = send(proxy(HttpRequest.POST("/join-events", body)));

        assertEquals(422, refused.status(), "a payload with a player UUID is refused");
        assertFalse(refused.body().contains(playerUuid), "the refused value is not repeated");
        assertEquals(0, store.eventCount(), "nothing is stored");
    }

    @Test
    void anAnswerTwoHoursAfterTheJoinIsStored() {
        UUID id = UUID.fromString("9c3b2e4a-5d6f-4071-8c8d-9e0f1a2b3c4d");
        send(proxy(HttpRequest.POST("/join-events", event(id, true, clock.instant().minus(Duration.ofHours(2))))));

        Reply answered = send(proxy(HttpRequest.POST("/join-events/" + id + "/source-answer", "{\"source\":\"youtube\"}")));

        assertEquals(201, answered.status(), "an answer within 24 hours is stored");
        assertEquals(1, store.answerCount(), "one answer is stored");
    }

    @Test
    void anAnswerTwentyFiveHoursAfterTheJoinIsRefusedAndStoresNothing() {
        UUID id = UUID.fromString("ad4c3f5b-6e70-4182-9d9e-0f1a2b3c4d5e");
        send(proxy(HttpRequest.POST("/join-events", event(id, true, clock.instant().minus(Duration.ofHours(25))))));

        Reply late = send(proxy(HttpRequest.POST("/join-events/" + id + "/source-answer", "{\"source\":\"youtube\"}")));

        assertEquals(422, late.status(), "an answer after 24 hours is refused");
        assertEquals(0, store.answerCount(), "nothing is stored");
    }

    @Test
    void aSourceOutsideTheFixedChoicesIsRefused() {
        UUID id = UUID.fromString("be5d4a6c-7f81-4293-8e0f-1a2b3c4d5e6f");
        send(proxy(HttpRequest.POST("/join-events", event(id, true, clock.instant()))));

        Reply refused = send(proxy(HttpRequest.POST("/join-events/" + id + "/source-answer", "{\"source\":\"reddit\"}")));

        assertEquals(422, refused.status(), "a value outside the choices is refused");
    }

    @Test
    void anAnswerForAnUnknownJoinAnswers404() {
        Reply missing = send(proxy(HttpRequest.POST("/join-events/" + UUID.randomUUID() + "/source-answer",
                "{\"source\":\"twitch\"}")));

        assertEquals(404, missing.status(), "an answer for a join that was never stored is not found");
    }

    @Test
    void anAdminSetsTheJoinDomainAndAnEditorCannot() {
        String domain = "{\"joinDomain\":\"onelitefeather.net\",\"attributionWindowHours\":48}";

        Reply byEditor = send(as(HttpRequest.PUT("/join-attribution/settings", domain), "EDITOR"));
        Reply byAdmin = send(as(HttpRequest.PUT("/join-attribution/settings", domain), "ADMIN"));
        Reply upperCase = send(as(HttpRequest.PUT("/join-attribution/settings", "{\"joinDomain\":\"OneLiteFeather.net\"}"),
                "ADMIN"));

        assertEquals(403, byEditor.status(), "only the admin role sets the join domain");
        assertEquals(200, byAdmin.status(), "the admin sets it");
        assertTrue(byAdmin.body().contains("\"attributionWindowHours\":48"), byAdmin.body());
        assertEquals(422, upperCase.status(), "an upper-case domain is refused");
    }

    @Test
    void aViewerReadsTheNewPlayerReportOfAPeriod() {
        UUID id = UUID.fromString("cf6e5b7d-8091-4a4e-8f1a-2b3c4d5e6f70");
        send(proxy(HttpRequest.POST("/join-events", event(id, true, clock.instant()))));
        String day = clock.instant().atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDate().toString();

        Reply report = send(as(HttpRequest.GET("/join-attribution/new-players?from=" + day + "&to=" + day), "VIEWER"));

        assertEquals(200, report.status(), "every signed-in role reads the report");
        assertTrue(report.body().contains("\"total\":1"), report.body());
        assertTrue(report.body().contains("stated by players"), report.body());
    }

    @Test
    void aPostOfASeriesHiddenFromTheViewerAnswers404() {
        UUID post = UUID.fromString("d07f6c8e-91a2-4b5f-8a2b-3c4d5e6f7081");
        store.addPost(new AttributablePost(post, "youtube", clock.instant().minus(Duration.ofHours(1)),
                UUID.fromString("e18a7d9f-a2b3-4c60-8b3c-4d5e6f708192"), "Geheim", Set.of(Role.ADMIN)));

        Reply hidden = send(as(HttpRequest.GET("/join-attribution/posts/" + post), "VIEWER"));

        assertEquals(404, hidden.status(), "a post of a series the viewer may not see is not found");
    }

    private String event(UUID id, boolean firstJoin, Instant joinedAt) {
        return "{\"eventId\":\"" + id + "\",\"firstJoin\":" + firstJoin + ",\"joinedAt\":\"" + joinedAt
                + "\",\"pseudonym\":\"" + PSEUDONYM + "\"}";
    }

    private static MutableHttpRequest<?> proxy(MutableHttpRequest<?> request) {
        return request.header("Authorization", PROXY).contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private static MutableHttpRequest<?> as(MutableHttpRequest<?> request, String roles) {
        String person = roles.equals("ADMIN") ? ADMIN : VIEWER;
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person)
                .header(HeaderIdentityFilter.ROLES_HEADER, roles)
                .contentType(MediaType.APPLICATION_JSON_TYPE);
    }

    private Reply send(MutableHttpRequest<?> request) {
        request.contentType(MediaType.APPLICATION_JSON_TYPE);
        try {
            HttpResponse<String> response = client.toBlocking().exchange(request, String.class);
            return new Reply(response.code(), response.body());
        } catch (HttpClientResponseException refused) {
            return new Reply(refused.getStatus().getCode(), refused.getResponse().getBody(String.class).orElse(""));
        }
    }

    private record Reply(int status, String body) {
    }
}
