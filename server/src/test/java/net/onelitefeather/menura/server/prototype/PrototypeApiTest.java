package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prototype environment without a database. Requests name their caller with the test identity headers (see
 * HeaderIdentityFilter). The tests only read, so their order does not matter.
 */
@MicronautTest(environments = "prototype")
class PrototypeApiTest {

    private static final String DEMO_HEADER = "X-Menura-Demo-Data";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ApplicationContext context;

    @Test
    void healthAnswers200AndNamesTheInMemoryStore() {
        Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/health"), Map.class);

        assertEquals(Map.of("status", "UP", "database", "IN_MEMORY"), body);
    }

    @Test
    void noDataSourceBeanExistsInThePrototypeEnvironment() {
        assertFalse(context.containsBean(DataSource.class), "the prototype must not create a database connection pool");
    }

    @Test
    void seriesWithoutASessionIsRefusedWith401AsProblemDetails() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/api/prototype/series"), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
        assertEquals("true", refused.getResponse().header(DEMO_HEADER), "even a refusal carries the demo header");
    }

    @Test
    void signedInViewerGetsTheFourSeriesWithTheDemoHeader() {
        var response = client.toBlocking().exchange(viewer(HttpRequest.GET("/api/prototype/series")),
                Argument.listOf(Model.Series.class));

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("true", response.header(DEMO_HEADER));
        List<Model.Series> series = response.body();
        assertEquals(List.of("k8s-30s", "live-migration", "wir-bauen-olf", "survival"),
                series.stream().map(Model.Series::id).toList());
    }

    @Test
    void writingSeriesWithTheViewerRoleIsRefusedWith403() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/series", Map.of()),
                        "VIEWER"), Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    void anEditorPassesTheRoleCheckAndGetsTheDomainAnswer() {
        // The hashtag is already used by k8s-30s, so the store refuses it and nothing is created.
        Map<String, Object> body = Map.ofEntries(
                Map.entry("name", "Neu"),
                Map.entry("badge", Map.of("de", "Neu", "en", "New")),
                Map.entry("colorToken", "series-k8s"),
                Map.entry("badgeTextColor", "#FFFFFF"),
                Map.entry("openingFormula", "Kurz:"),
                Map.entry("hashtag", "#k8sin30"),
                Map.entry("audience", "developers"),
                Map.entry("weekday", "TUESDAY"),
                Map.entry("slot", "18:00"),
                Map.entry("playlists", Map.of()),
                Map.entry("active", true));

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/series", body),
                        "EDITOR"), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void aPostWithoutAPlatformAverageHasNullNotZero() {
        List<Model.Post> posts = client.toBlocking().retrieve(
                viewer(HttpRequest.GET("/api/prototype/posts?channelId=tt-de")), Argument.listOf(Model.Post.class));

        assertFalse(posts.isEmpty());
        for (Model.Post post : posts) {
            assertNull(post.avgViewPercent(), "TikTok does not deliver the average in the seed");
        }
    }

    @Test
    void aSeriesWithoutAnAudienceIsRefusedWith422NotAServerError() {
        Map<String, Object> body = validSeriesBody();
        body.remove("audience");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/series", body),
                        "EDITOR"), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void aNullPlaylistValueIsRefusedWith422NotAServerError() {
        Map<String, Object> body = validSeriesBody();
        Map<String, String> playlists = new HashMap<>();
        playlists.put("yt-de", null);
        body.put("playlists", playlists);

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/series", body),
                        "EDITOR"), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void aRefusedSeriesDoesNotEchoTheRawHashtagInTheProblemDetail() {
        Map<String, Object> body = validSeriesBody();
        body.put("hashtag", "#Raw-Echo-Value");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/series", body),
                        "EDITOR"), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
        String problemBody = refused.getResponse().getBody(Map.class).map(Object::toString).orElse("");
        assertTrue(problemBody.contains("hashtag"), problemBody);
        assertFalse(problemBody.contains("Raw-Echo-Value"), problemBody);
    }

    private static Map<String, Object> validSeriesBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Neu");
        body.put("badge", Map.of("de", "Neu", "en", "New"));
        body.put("colorToken", "series-k8s");
        body.put("badgeTextColor", "#FFFFFF");
        body.put("openingFormula", "Kurz:");
        body.put("hashtag", "#neu");
        body.put("audience", "developers");
        body.put("weekday", "THURSDAY");
        body.put("slot", "18:00");
        body.put("playlists", Map.of());
        return body;
    }

    private static MutableHttpRequest<?> viewer(MutableHttpRequest<?> request) {
        return signedIn(request, "VIEWER");
    }

    private static MutableHttpRequest<?> signedIn(MutableHttpRequest<?> request, String roles) {
        return request.header("X-Test-Person", "tester-1").header("X-Test-Roles", roles);
    }
}
