package net.onelitefeather.menura.server.prototype.schedule;

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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP surface of the calendar: role checks and the read endpoints. Only refused writes are sent, so the shared
 * context stays unchanged and the tests do not depend on each other.
 */
@MicronautTest(environments = "prototype")
class PrototypeScheduleApiTest {

    private static final String DEMO_HEADER = "X-Menura-Demo-Data";

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void platformsAnswerForASignedInViewerWithTheDemoHeader() {
        var response = client.toBlocking().exchange(viewer(HttpRequest.GET("/api/prototype/platforms")), Map.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("true", response.header(DEMO_HEADER));
        assertTrue(response.body().containsKey("youtube"), "keyed by platform");
    }

    @Test
    void publicationsIncludeThePublishedPostsForAViewer() {
        List<Map<String, Object>> publications = client.toBlocking().retrieve(
                viewer(HttpRequest.GET("/api/prototype/publications?status=published")),
                Argument.listOf(Map.class)).stream().map(map -> (Map<String, Object>) map).toList();

        assertTrue(publications.stream().anyMatch(p -> "pub-p-41".equals(p.get("id"))),
                "the v1 post p-41 is listed as published");
    }

    @Test
    void writingAPublicationWithTheViewerRoleIsRefusedWith403() {
        Map<String, Object> body = Map.of("assetId", "a-107", "channelIds", List.of("yt-de"),
                "publishAt", "2026-11-12T17:00:00Z");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/publications", body),
                        "VIEWER"), Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    void deletingAPublicationWithTheViewerRoleIsRefusedWith403() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.DELETE("/api/prototype/publications/pub-1"),
                        "VIEWER"), Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    void planningASeriesWithTheViewerRoleIsRefusedWith403() {
        Map<String, Object> body = Map.of("channelIds", List.of("yt-de"), "count", 1);

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/series/k8s-30s/plan",
                        body), "VIEWER"), Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    void publicationsWithoutASessionAreRefusedWith401() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/api/prototype/publications"), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus());
    }

    private static MutableHttpRequest<?> viewer(MutableHttpRequest<?> request) {
        return signedIn(request, "VIEWER");
    }

    private static MutableHttpRequest<?> signedIn(MutableHttpRequest<?> request, String roles) {
        return request.header("X-Test-Person", "tester-1").header("X-Test-Roles", roles);
    }
}
