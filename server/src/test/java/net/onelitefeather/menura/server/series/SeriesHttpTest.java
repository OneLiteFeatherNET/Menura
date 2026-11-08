package net.onelitefeather.menura.server.series;

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
import net.onelitefeather.menura.core.series.support.InMemorySeriesStore;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The series over HTTP with the rows and the colours in memory. Each test clears the fakes first, so no test depends on
 * another.
 */
@MicronautTest(rebuildContext = true)
class SeriesHttpTest {

    private static final String EDITOR = "editor-1";
    private static final String VIEWER = "viewer-1";
    private static final String OPERATOR = "operator-1";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000002");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemorySeriesStore series;

    @Inject
    InMemoryChannelStore channels;

    @BeforeEach
    void resetFakes() {
        series.clear();
        channels.clear();
    }

    @Test
    void anEditorCreatesASeriesAndAViewerReadsItInTheCatalogue() throws Exception {
        create(EDITOR, "EDITOR", draft("Kubernetes in 30 s", "#kubernetes30s", "primary", "on-primary"));

        List<?> listed = (List<?>) get(VIEWER, "VIEWER", "/series");

        assertEquals(1, listed.size(), "the viewer sees the one series");
        assertEquals("Kubernetes in 30 s", ((Map<?, ?>) listed.get(0)).get("name"), "the viewer reads the name");
    }

    @Test
    void aViewerCannotCreateASeries() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> create(VIEWER, "VIEWER", draft("Nope", "#nope", "primary", "on-primary")));

        assertEquals(403, refused.getStatus().getCode(), "only an editor writes a series");
    }

    @Test
    void aRawHexColourIsRefusedWithProblemDetails() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> create(EDITOR, "EDITOR", draft("Hex", "#hexcolour", "#6638e4", "on-primary")));

        assertEquals(400, refused.getStatus().getCode(), "a hex value is refused");
        assertTrue(String.valueOf(refused.getResponse().getBody(Map.class).orElse(Map.of()))
                .contains("Invalid series"), "the refusal is a Problem Details answer");
    }

    @Test
    void aBadgeTextBelowTheContrastThresholdIsRefused() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> create(EDITOR, "EDITOR", draft("Pale", "#paleseries", "pale", "on-primary")));

        assertEquals(400, refused.getStatus().getCode(), "a pair below 4.5:1 is refused");
        assertEquals(0, series.writes(), "nothing is stored");
    }

    @Test
    void theRenderBundleIsRefusedForAViewer() {
        seedChannel();

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> get(VIEWER, "VIEWER", "/channels/" + CHANNEL + "/series-render-bundle"));

        assertEquals(403, refused.getStatus().getCode(), "the render bundle is for the renderer's operator role only");
    }

    @Test
    void theRenderBundleForAnUnknownChannelAnswersNotFound() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> get(OPERATOR, "OPERATOR", "/channels/" + UUID.randomUUID() + "/series-render-bundle"));

        assertEquals(404, refused.getStatus().getCode(), "an unknown channel answers 404");
    }

    @Test
    void theRenderBundleHasNoCallToActionWithoutAJoinDomain() throws Exception {
        seedChannel();
        create(EDITOR, "EDITOR", draft("Erste", "#erste", "primary", "on-primary"));

        Map<?, ?> bundle = (Map<?, ?>) get(OPERATOR, "OPERATOR", "/channels/" + CHANNEL + "/series-render-bundle");

        assertNull(bundle.get("callToAction"), "no join domain, no call to action");
        assertEquals(1, ((List<?>) bundle.get("series")).size(), "the active series is delivered");
    }

    @Test
    void theOverviewWarnsThatNoPlayersSeriesExists() throws Exception {
        create(EDITOR, "EDITOR", draft("Devs", "#devs", "primary", "on-primary"));

        Map<?, ?> overview = (Map<?, ?>) get(VIEWER, "VIEWER", "/series/overview?weeks=2");

        assertEquals(List.of("no_players_series"), overview.get("warnings"), "the audience warning is shown");
    }

    private void seedChannel() {
        channels.insertChannel(null, new Channel(CHANNEL, "listing", "UC-test", "Test channel", ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));
    }

    private static Map<String, Object> draft(String name, String hashtag, String colour, String badge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("badgeText", Map.of("de", "Kurz erklärt"));
        body.put("colourToken", colour);
        body.put("badgeToken", badge);
        body.put("openingFormula", "Kurz erklärt:");
        body.put("layoutTemplate", "short-9x16");
        body.put("hashtag", hashtag);
        body.put("audience", "developers");
        body.put("weekday", 1);
        body.put("postingSlot", 18);
        return body;
    }

    private HttpResponse<Map> create(String person, String role, Map<String, Object> body) {
        MutableHttpRequest<?> request = HttpRequest.POST("/series", body)
                .header(HeaderIdentityFilter.PERSON_HEADER, person)
                .header(HeaderIdentityFilter.ROLES_HEADER, role);
        return client.toBlocking().exchange(request, Map.class);
    }

    private Object get(String person, String role, String uri) {
        MutableHttpRequest<?> request = HttpRequest.GET(uri)
                .header(HeaderIdentityFilter.PERSON_HEADER, person)
                .header(HeaderIdentityFilter.ROLES_HEADER, role);
        return client.toBlocking().exchange(request, Object.class).body();
    }
}
