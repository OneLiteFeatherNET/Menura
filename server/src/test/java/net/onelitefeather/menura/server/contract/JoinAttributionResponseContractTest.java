package net.onelitefeather.menura.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
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
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The join attribution responses against the contract: every member the contract requires is present, and the nullable
 * ones may be null. The proxy's calls use the fixed test credential; the reads use a viewer and an admin.
 */
@MicronautTest(rebuildContext = true)
class JoinAttributionResponseContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROXY = "Bearer test-join-proxy-credential";
    private static final String PSEUDONYM = "f".repeat(64);

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryJoinStore store;

    @Inject
    Clock clock;

    private final ResponseContract contract = ResponseContract.load();

    @BeforeEach
    void startFromAnEmptyStore() {
        store.clear();
    }

    @Test
    void anEmptySettingsReadConformsAndShowsNoDomain() throws Exception {
        JsonNode settings = get("/join-attribution/settings", "ADMIN");

        assertConforms("JoinSettings", settings);
        assertTrue(settings.get("joinDomain").isNull(), "no domain until an admin sets one");
    }

    @Test
    void aStoredFirstJoinAndItsAnswerConformToTheReceipts() throws Exception {
        UUID event = UUID.fromString("0f1e2d3c-4b5a-4968-8778-6a5b4c3d2e1f");
        String joinedAt = clock.instant().toString();

        JsonNode receipt = send(HttpRequest.POST("/join-events", "{\"eventId\":\"" + event + "\",\"firstJoin\":true,"
                + "\"joinedAt\":\"" + joinedAt + "\",\"pseudonym\":\"" + PSEUDONYM + "\"}").header("Authorization", PROXY));
        JsonNode answer = send(HttpRequest.POST("/join-events/" + event + "/source-answer", "{\"source\":\"instagram\"}")
                .header("Authorization", PROXY));

        assertConforms("JoinEventReceipt", receipt);
        assertConforms("JoinSourceAnswerReceipt", answer);
    }

    @Test
    void theNewPlayerReportConformsWithItsStatedFigures() throws Exception {
        UUID event = UUID.fromString("1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9");
        send(HttpRequest.POST("/join-events", "{\"eventId\":\"" + event + "\",\"firstJoin\":true,\"joinedAt\":\""
                + clock.instant() + "\",\"pseudonym\":\"" + PSEUDONYM + "\"}").header("Authorization", PROXY));
        String day = clock.instant().atZone(ZoneId.of("Europe/Berlin")).toLocalDate().toString();

        JsonNode report = get("/join-attribution/new-players?from=" + day + "&to=" + day, "VIEWER");

        assertConforms("JoinNewPlayers", report);
        assertTrue(report.get("periods").get(0).get("stated").size() == 6, "every stated source is listed");
    }

    @Test
    void aPostEstimateAndTheSeriesEstimatesConform() throws Exception {
        UUID post = UUID.fromString("2b3c4d5e-6f70-4182-9394-b5c6d7e8f901");
        store.addPost(new AttributablePost(post, "youtube", clock.instant().minus(Duration.ofHours(3)), null, null,
                Set.of()));

        JsonNode estimate = get("/join-attribution/posts/" + post, "VIEWER");
        JsonNode series = get("/join-attribution/series?from=" + clock.instant().atZone(ZoneId.of("Europe/Berlin"))
                .toLocalDate(), "VIEWER");

        assertConforms("JoinPostEstimate", estimate);
        assertConforms("JoinSeriesEstimates", series);
        assertTrue(series.get("series").get(0).get("seriesId").isNull(), "posts without a series carry a null id");
    }

    private void assertConforms(String schema, JsonNode body) {
        assertTrue(contract.violations(schema, body).isEmpty(),
                schema + " does not match the contract: " + contract.violations(schema, body) + " in " + body);
    }

    private JsonNode get(String uri, String roles) throws Exception {
        MutableHttpRequest<?> request = HttpRequest.GET(uri)
                .header(HeaderIdentityFilter.PERSON_HEADER, "contract-" + roles.toLowerCase())
                .header(HeaderIdentityFilter.ROLES_HEADER, roles);
        return send(request);
    }

    private JsonNode send(MutableHttpRequest<?> request) throws Exception {
        request.contentType(MediaType.APPLICATION_JSON_TYPE);
        return JSON.readTree(client.toBlocking().exchange(request, String.class).body());
    }
}
