package net.onelitefeather.menura.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.analytics.MetricRollups;
import net.onelitefeather.menura.core.analytics.MetricStore;
import net.onelitefeather.menura.core.analytics.Metrics;
import net.onelitefeather.menura.core.analytics.support.InMemoryMetricStore;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.support.InMemoryExternalPostStore;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Content analytics over HTTP, with the metric store in memory. Each test clears the fakes first, so no test depends on
 * another, and every response is checked against the contract.
 */
@MicronautTest(rebuildContext = true)
class AnalyticsResponseContractTest {

    private static final String VIEWER = "viewer-1";
    private static final Instant PUBLISHED = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000003");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryMetricStore metrics;

    @Inject
    InMemoryExternalPostStore posts;

    @Inject
    Transactions transactions;

    private final ResponseContract contract = ResponseContract.load();

    @BeforeEach
    void resetFakes() {
        metrics.clear();
        posts.clear();
    }

    @Test
    void anEmptyOverviewConformsToTheContractAndShowsNoValue() throws Exception {
        JsonNode overview = get("/analytics/overview?from=2026-10-01&to=2026-12-31");

        assertConforms("AnalyticsOverview", overview);
        assertTrue(overview.get("totals").get("metrics").get(0).get("value").isNull(),
                "views are unavailable, not zero, when no post has a value");
    }

    @Test
    void theOverviewSumsTheLatestValueOfAPublishedPost() throws Exception {
        ExternalPost post = publishedPost();
        metrics.ensurePartitions(null, NOW);
        metrics.storeReading(null, new MetricStore.Reading(post, Map.of(Metrics.VIEWS, 120L), null, NOW));
        rollUp();

        JsonNode overview = get("/analytics/overview?from=2026-10-01&to=2026-12-31");

        assertConforms("AnalyticsOverview", overview);
        assertEquals(120.0, overview.get("totals").get("metrics").get(0).get("value").asDouble(),
                "the total views are the latest value of the post");
    }

    @Test
    void anEqualAgeComparisonConformsWithTwentyFourHourSlots() throws Exception {
        JsonNode comparison = get("/analytics/equal-age?metric=views&offsetHours=24&by=hour");

        assertConforms("AnalyticsEqualAge", comparison);
        assertEquals(24, comparison.get("slots").size(), "every hour of the day is a slot");
    }

    @Test
    void aSeriesComparisonConformsToTheContract() throws Exception {
        JsonNode comparison = get("/analytics/series?from=2026-10-01&to=2026-12-31");

        assertConforms("AnalyticsSeriesComparison", comparison);
    }

    @Test
    void aPostWithItsSnapshotConformsToTheContract() throws Exception {
        ExternalPost post = publishedPost();
        metrics.ensurePartitions(null, NOW);
        metrics.storeReading(null, new MetricStore.Reading(post, Map.of(Metrics.VIEWS, 120L), null, NOW));
        rollUp();
        metrics.storeSnapshots(null, java.util.List.of(new MetricStore.SnapshotValue(post.id(), 60, Metrics.VIEWS, 90L,
                NOW, 60)));

        JsonNode detail = get("/analytics/external-posts/" + post.id());

        assertConforms("AnalyticsPost", detail);
        assertEquals("views", detail.get("snapshots").get(0).get("metric").asText(), "the snapshot is listed");
    }

    @Test
    void anOffsetThatIsNotOnTheScheduleAnswersBadRequest() {
        HttpClientResponseException refused = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> get("/analytics/equal-age?offsetHours=5"));

        assertEquals(400, refused.getStatus().getCode(), "an offset that is not on the schedule is refused");
    }

    @Test
    void aMissingPostAnswersNotFound() {
        HttpClientResponseException missing = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> get("/analytics/external-posts/" + UUID.nameUUIDFromBytes("missing".getBytes())));

        assertEquals(404, missing.getStatus().getCode(), "a post that does not exist answers 404");
    }

    @Test
    void aSeriesWithAnInternalMetricShowsItsEstimateFlagAndConformsToTheContract() throws Exception {
        UUID series = UUID.fromString("5e71e500-0000-4000-8000-000000000001");
        metrics.ensurePartitions(null, NOW);
        metrics.storeInternal(null, new MetricStore.InternalPoint("joins", "first_joins", null, null, series,
                LocalDate.of(2026, 11, 2), 5L, true), NOW);
        rollUp();

        JsonNode comparison = get("/analytics/series?from=2026-10-01&to=2026-12-31");

        assertConforms("AnalyticsSeriesComparison", comparison);
        JsonNode row = comparison.get("series").get(0);
        assertEquals(series.toString(), row.get("seriesId").asText(), "the internal values belong to their series");
        assertEquals("first_joins", row.get("internal").get(0).get("metric").asText(), "the internal metric is listed");
        assertTrue(row.get("internal").get(0).get("estimate").asBoolean(), "the estimate flag is shown on the read");
    }

    /** Rebuilds the rollups, as the rollup job does after a collection. */
    private void rollUp() throws SQLException {
        new MetricRollups(transactions, metrics).rebuild();
    }

    private ExternalPost publishedPost() throws Exception {
        PublishedItem item = new PublishedItem("v1", "https://video.example.test/v1", "Title", PUBLISHED, "short");
        ExternalPost post = ExternalPost.create(UUID.nameUUIDFromBytes("post-v1".getBytes()), CHANNEL, "youtube", item,
                PUBLISHED);
        posts.insert(null, post);
        return post;
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
