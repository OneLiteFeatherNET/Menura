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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The post, plan and calendar endpoints over HTTP, with the rows in memory. Each test clears the fakes first. The roles
 * and the channel of each request come from the test headers; the production rules (roles, the web-only approval, the
 * dry run) run as they are.
 */
@MicronautTest(rebuildContext = true)
class PostHttpTest {

    private static final String EDITOR = "editor-1";
    private static final String PUBLISHER = "publisher-1";
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000011");
    private static final UUID VIDEO = UUID.fromString("a55e7000-0000-4000-8000-000000000011");
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
        channels.insertChannel(null, new Channel(CHANNEL, "youtube", "UC-posts", "Posts channel", ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));
        posts.registerAsset(VIDEO, "video");
    }

    @Test
    void anEditorCreatesADraftThatIsReadBackWithItsAuthor() throws Exception {
        Map<?, ?> created = body(exchange(post(EDITOR, "EDITOR", "/posts", draft("Highlight", null))), 201);

        assertEquals("draft", created.get("status"), "a new post is a draft");
        assertTrue(created.get("authorId") != null, "the author is recorded");
        Map<?, ?> read = body(exchange(get(EDITOR, "EDITOR", "/posts/" + created.get("id"))), 200);
        assertEquals("Highlight", read.get("title"), "the draft reads back with its title");
    }

    @Test
    void anEditorCannotApprove() throws Exception {
        String post = createDraft();
        submit(post);

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> exchange(post(EDITOR, "EDITOR", "/posts/" + post + "/approval", Map.of())));

        assertEquals(403, failure.getStatus().getCode(), "only the publisher role approves");
    }

    @Test
    void aPublisherTokenCannotApproveEvenWithTheRole() throws Exception {
        String post = createDraft();
        submit(post);

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> exchange(post(PUBLISHER, "PUBLISHER", "mcp", "/posts/" + post + "/approval", Map.of())));

        assertEquals(403, failure.getStatus().getCode(), "an MCP or token session never approves");
    }

    @Test
    void aPublisherApprovesAndSchedulesInTheWebApplication() throws Exception {
        String post = createDraft();
        declareMadeForKids(post, false);
        submit(post);

        Map<?, ?> approved = body(exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/approval", Map.of())), 200);
        Map<?, ?> scheduled = body(exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/schedule", Map.of())), 200);

        assertEquals("approved", approved.get("status"), "approval moves the post to approved");
        assertTrue(approved.get("approvedBy") != null, "the publisher is recorded");
        assertEquals("scheduled", scheduled.get("status"), "the schedule moves the post to scheduled");
    }

    @Test
    void aScheduleBlockedByTheDryRunAnswersWithItsProblems() throws Exception {
        String post = createDraft("A title that is far longer than the one hundred characters that a YouTube video title may use, so the dry run blocks it");
        submit(post);
        exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/approval", Map.of()));

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/schedule", Map.of())));

        assertEquals(422, failure.getStatus().getCode(), "a blocked schedule is 422");
        Map<?, ?> problem = (Map<?, ?>) failure.getResponse().getBody(Map.class).orElseThrow();
        List<?> problems = (List<?>) problem.get("problems");
        assertTrue(problems.stream().anyMatch(item -> "title_too_long".equals(((Map<?, ?>) item).get("code"))),
                "the title limit is named in the problems");
    }

    @Test
    void aPostWithoutItsMadeForKidsDeclarationIsNotScheduled() throws Exception {
        String post = createDraft();
        submit(post);
        exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/approval", Map.of()));

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/schedule", Map.of())));

        assertEquals(422, failure.getStatus().getCode(), "a schedule without the declaration is 422");
        Map<?, ?> problem = (Map<?, ?>) failure.getResponse().getBody(Map.class).orElseThrow();
        List<?> problems = (List<?>) problem.get("problems");
        assertTrue(problems.stream().anyMatch(item -> "made_for_kids_missing".equals(((Map<?, ?>) item).get("code"))),
                "the missing made-for-kids answer is named in the problems");
    }

    @Test
    void aStatusSkipAnswersConflict() throws Exception {
        String post = createDraft();

        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/schedule", Map.of())));

        assertEquals(409, failure.getStatus().getCode(), "a draft cannot be scheduled without approval");
    }

    @Test
    void aRevisionIsProposedByAnEditorAndAcceptedOnlyInTheWeb() throws Exception {
        String post = createDraft();
        submit(post);
        exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/approval", Map.of()));
        Map<?, ?> revision = body(exchange(post(EDITOR, "EDITOR", "/posts/" + post + "/revisions",
                Map.of("rationale", "A clearer title.", "title", "Clearer title"))), 201);

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> exchange(post(PUBLISHER, "PUBLISHER", "mcp", "/posts/" + post + "/revisions/" + revision.get("id")
                        + "/acceptance", Map.of())));
        Map<?, ?> accepted = body(exchange(post(PUBLISHER, "PUBLISHER", "/posts/" + post + "/revisions/" + revision.get("id")
                + "/acceptance", Map.of())), 200);

        assertEquals(403, refused.getStatus().getCode(), "accepting from a token is refused");
        assertEquals("in_review", accepted.get("status"), "the accepted revision returns the post to review");
        assertEquals("Clearer title", accepted.get("title"), "the accepted text is stored");
    }

    @Test
    void aPlanNamesAChannelThatDoesNotExistAndStoresTheOthers() throws Exception {
        Map<?, ?> plan = body(exchange(post(EDITOR, "EDITOR", "/post-plans", Map.of("assetId", VIDEO.toString(),
                "channels", List.of(
                        Map.of("channelId", CHANNEL.toString(), "scheduledAt", SLOT, "title", "Kept"),
                        Map.of("channelId", UUID.randomUUID().toString(), "scheduledAt", SLOT, "title", "Refused"))))), 201);

        assertEquals(1, ((List<?>) plan.get("posts")).size(), "the usable channel gets its draft");
        assertEquals(1, ((List<?>) plan.get("refused")).size(), "the unknown channel is refused and named");
    }

    @Test
    void theCalendarShowsAPostAtItsBerlinTime() throws Exception {
        createDraftAt("2026-11-08T23:30:00Z");

        Map<?, ?> page = body(exchange(get(EDITOR, "EDITOR", "/calendar?view=week&date=2026-11-09")), 200);

        assertEquals("Europe/Berlin", page.get("timeZone"), "the installation time zone is shown");
        Map<?, ?> item = (Map<?, ?>) ((List<?>) page.get("items")).get(0);
        assertEquals("2026-11-09", item.get("localDate"), "23:30 UTC on Sunday is Monday in Berlin");
        assertEquals("00:30", item.get("localTime"), "and it is 00:30 there");
    }

    @Test
    void theCalendarFiltersByStatus() throws Exception {
        createDraftAt("2026-11-09T17:00:00Z");

        Map<?, ?> none = body(exchange(get(EDITOR, "EDITOR", "/calendar?view=list&date=2026-11-09&days=3&status=scheduled")), 200);

        assertTrue(((List<?>) none.get("items")).isEmpty(), "a draft is not listed under the scheduled filter");
    }

    @Test
    void aCalendarWithAnUnknownViewIsABadRequest() throws Exception {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> exchange(get(EDITOR, "EDITOR", "/calendar?view=year")));

        assertEquals(400, failure.getStatus().getCode(), "only month, week and list exist");
    }

    @Test
    void anAnonymousCallerCannotReadTheCalendar() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/calendar"), Map.class));

        assertEquals(401, failure.getStatus().getCode(), "the calendar is for signed-in people only");
    }

    // ----------------------------------------------------------------- helpers

    private String createDraft() throws Exception {
        return createDraft("Highlight");
    }

    private String createDraft(String title) throws Exception {
        return String.valueOf(body(exchange(post(EDITOR, "EDITOR", "/posts", draft(title, null))), 201).get("id"));
    }

    private void createDraftAt(String at) throws Exception {
        body(exchange(post(EDITOR, "EDITOR", "/posts", draft("Timed", at))), 201);
    }

    private void declareMadeForKids(String post, boolean madeForKids) throws Exception {
        body(exchange(withIdentity(HttpRequest.PUT("/posts/" + post + "/youtube-declaration", Map.of("madeForKids", madeForKids)),
                EDITOR, "EDITOR", "web")), 200);
    }

    private void submit(String post) throws Exception {
        exchange(post(EDITOR, "EDITOR", "/posts/" + post + "/submission", Map.of()));
    }

    private static Map<String, Object> draft(String title, String scheduledAt) {
        java.util.HashMap<String, Object> draft = new java.util.HashMap<>();
        draft.put("channelId", CHANNEL.toString());
        draft.put("assetId", VIDEO.toString());
        draft.put("title", title);
        draft.put("scheduledAt", scheduledAt == null ? SLOT : scheduledAt);
        draft.put("visibility", "private");
        return draft;
    }

    private HttpResponse<Map> exchange(MutableHttpRequest<?> request) {
        return client.toBlocking().exchange(request, Map.class);
    }

    private static Map<?, ?> body(HttpResponse<Map> response, int status) {
        assertEquals(status, response.code(), "the status code of the response");
        return response.getBody(Map.class).orElseThrow();
    }

    private static MutableHttpRequest<?> get(String person, String roles, String uri) {
        return withIdentity(HttpRequest.GET(uri), person, roles, "web");
    }

    private static MutableHttpRequest<?> post(String person, String roles, String uri, Object body) {
        return withIdentity(HttpRequest.POST(uri, body), person, roles, "web");
    }

    private static MutableHttpRequest<?> post(String person, String roles, String channel, String uri, Object body) {
        return withIdentity(HttpRequest.POST(uri, body), person, roles, channel);
    }

    private static MutableHttpRequest<?> withIdentity(MutableHttpRequest<?> request, String person, String roles,
                                                      String channel) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person)
                .header(HeaderIdentityFilter.ROLES_HEADER, roles)
                .header(HeaderIdentityFilter.CHANNEL_HEADER, channel);
    }
}
