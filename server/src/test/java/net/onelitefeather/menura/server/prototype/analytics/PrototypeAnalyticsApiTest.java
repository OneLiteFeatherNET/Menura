package net.onelitefeather.menura.server.prototype.analytics;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The analytics endpoints in the prototype environment. The clock is fixed at the seed time, so the offsets that have
 * come by then are the same on every run.
 */
@MicronautTest(environments = "prototype")
class PrototypeAnalyticsApiTest {

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(Clock.class)
    Clock clock() {
        return Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void theMetricsEndpointServesTheCatalogueWithItsPlatforms() {
        List<Map<String, Object>> metrics = client.toBlocking().retrieve(
                viewer(HttpRequest.GET("/api/prototype/analytics/metrics")), Argument.listOf(Map.class))
                .stream().map(m -> (Map<String, Object>) m).toList();

        assertEquals(MetricCatalogue.METRICS.size(), metrics.size());
        Map<String, Object> engaged = metrics.stream().filter(m -> "engagedViews".equals(m.get("id"))).findFirst()
                .orElseThrow();
        assertEquals("Engaged Views", engaged.get("label"));
        assertEquals(List.of("youtube"), engaged.get("platforms"));
    }

    @Test
    void anUnknownMetricIsRefusedWith422() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET(
                        "/api/prototype/analytics/aggregate?metric=clicks")), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void anUnknownOffsetIsRefusedWith422() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET(
                        "/api/prototype/analytics/aggregate?metric=views&offset=%2B3h")), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void anUnknownGroupByIsRefusedWith422() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET(
                        "/api/prototype/analytics/aggregate?metric=views&groupBy=month")), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void anUnknownChannelIsRefusedWith422() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET(
                        "/api/prototype/analytics/heatmap?metric=views&channelIds=xx-de")), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void theAggregateOfEngagedViewsListsTikTokAsMissing() {
        Map<String, Object> body = client.toBlocking().retrieve(
                viewer(HttpRequest.GET("/api/prototype/analytics/aggregate?metric=engagedViews&groupBy=channel")),
                Map.class);

        assertEquals(List.of("tt-de", "ig-de"), body.get("missing"));
    }

    @Test
    void theSnapshotsOfAPostEndWithTheOffsetsThatHaveComeByNow() {
        // Published 6 Nov 17:00 UTC: +24 h has come by 8 Nov 12:00, +48 h has not.
        List<Map<String, Object>> snapshots = snapshots("p-44");

        assertEquals(List.of("+1h", "+2h", "+4h", "+8h", "+24h"),
                snapshots.stream().map(s -> s.get("offset")).toList());
    }

    @Test
    void aTikTokSnapshotHasAnExplicitNullEngagedViews() {
        Map<String, Object> first = snapshots("p-42").get(0);

        assertTrue(first.containsKey("engagedViews"), "the key is served");
        assertNull(first.get("engagedViews"), "TikTok has no engaged views, which is null and never 0");
        assertNotNull(first.get("views"));
    }

    @Test
    void aSnapshotCarriesTheContractFields() {
        Map<String, Object> first = snapshots("p-41").get(0);

        assertEquals(Set.of("offset", "at", "views", "engagedViews", "likes", "comments", "shares"), first.keySet());
        assertEquals("+1h", first.get("offset"));
        assertEquals("2026-10-27T18:00:00Z", first.get("at"));
    }

    @Test
    void snapshotsOfAnUnknownPostAreNotFound() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET(
                        "/api/prototype/analytics/posts/p-999/snapshots")), Map.class));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
    }

    private List<Map<String, Object>> snapshots(String postId) {
        return client.toBlocking().retrieve(
                viewer(HttpRequest.GET("/api/prototype/analytics/posts/" + postId + "/snapshots")),
                Argument.listOf(Map.class)).stream().map(s -> (Map<String, Object>) s).toList();
    }

    private static MutableHttpRequest<?> viewer(MutableHttpRequest<?> request) {
        return request.header("X-Test-Person", "tester-1").header("X-Test-Roles", "VIEWER");
    }
}
