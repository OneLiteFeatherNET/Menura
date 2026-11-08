package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.AssetDetail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The pool endpoints over HTTP. The shared context is written by some tests, so every test that writes names its own
 * caller (a distinct test identity) and only asserts on the assets it created itself.
 */
@MicronautTest(environments = "prototype")
class PrototypePoolControllerTest {

    private static final String DEMO_HEADER = "X-Menura-Demo-Data";

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void peopleAnswersTheFourDemoPersonsWithTheDemoHeader() {
        var response = client.toBlocking().exchange(viewer(HttpRequest.GET("/api/prototype/people"), "pool-people"),
                Argument.listOf(Person.class));

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("true", response.header(DEMO_HEADER));
        assertTrue(response.body().stream().anyMatch(person -> person.id().equals("p-a")), "p-a is a demo person");
    }

    @Test
    void assetsWithoutASessionAreRefusedWith401AsProblemDetails() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/api/prototype/assets"), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void theKindFilterAnswersOnlyClips() {
        List<Asset> clips = client.toBlocking().retrieve(viewer(HttpRequest.GET("/api/prototype/assets?kind=clip"),
                "pool-kind"), Argument.listOf(Asset.class));

        assertFalse(clips.isEmpty());
        assertTrue(clips.stream().allMatch(asset -> asset.kind() == Asset.Kind.clip), "every result is a clip");
    }

    @Test
    void theTextFilterFindsTranscriptText() {
        List<Asset> found = client.toBlocking().retrieve(viewer(
                HttpRequest.GET("/api/prototype/assets?q=STABILISIERUNGSFENSTER"), "pool-text"),
                Argument.listOf(Asset.class));

        assertTrue(found.stream().anyMatch(asset -> asset.id().equals("a-100")), "the transcript of a-100 matches");
    }

    @Test
    void anUnknownKindFilterIsRefusedWith422() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET("/api/prototype/assets?kind=video"),
                        "pool-bad-kind"), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
    }

    @Test
    void anUnknownAssetIsA404ProblemDetails() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(viewer(HttpRequest.GET("/api/prototype/assets/a-999"),
                        "pool-missing"), Map.class));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void aPendingTranscriptShowsNoSegments() {
        AssetDetail detail = client.toBlocking().retrieve(viewer(HttpRequest.GET("/api/prototype/assets/a-107"),
                "pool-pending"), AssetDetail.class);

        assertTrue(detail.transcriptSegments().isEmpty(), "a pending transcript has no segments on the wire");
    }

    @Test
    void aViewerWritingTagsIsRefusedWith403() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.PUT("/api/prototype/assets/a-104/tags",
                        Map.of("tags", List.of("ebpf"), "people", List.of("p-c"))), "VIEWER", "pool-viewer-tags"),
                        Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    void aViewerCreatingAnAssetIsRefusedWith403() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/assets",
                        Map.of("kind", "raw", "title", "Viewer", "durationSeconds", 60)), "VIEWER",
                        "pool-viewer-create"), Map.class));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
    }

    @Test
    void anInvalidTagIsRefusedWith422AndNotEchoed() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(signedIn(HttpRequest.PUT("/api/prototype/assets/a-104/tags",
                        Map.of("tags", List.of("Raw-Echo-Value"), "people", List.of("p-c"))), "EDITOR",
                        "pool-bad-tag"), Map.class));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refused.getStatus());
        String problemBody = refused.getResponse().getBody(Map.class).map(Object::toString).orElse("");
        assertFalse(problemBody.contains("Raw-Echo-Value"), problemBody);
    }

    @Test
    void anEditorCreatesAnUploadAndSeesItAsOwnedByThemselves() {
        String caller = "pool-create-editor";
        Asset created = client.toBlocking().retrieve(signedIn(HttpRequest.POST("/api/prototype/assets",
                Map.of("kind", "raw", "title", "Upload per HTTP", "tags", List.of("ebpf"), "people", List.of(),
                        "durationSeconds", 600)), "EDITOR", caller), Asset.class);
        AssetDetail detail = client.toBlocking().retrieve(signedIn(HttpRequest.GET("/api/prototype/assets/" + created.id()),
                "EDITOR", caller), AssetDetail.class);

        List<Asset> mine = client.toBlocking().retrieve(signedIn(HttpRequest.GET("/api/prototype/assets?ownerId=me"),
                "EDITOR", caller), Argument.listOf(Asset.class));

        assertTrue(mine.stream().anyMatch(asset -> asset.id().equals(created.id())), "the upload is owned by the caller");
        assertEquals(Asset.Transcript.pending, created.transcript());
        assertTrue(detail.transcriptSegments().isEmpty(), "a new upload has no transcript yet");
    }

    @Test
    void anEditorCreatingAnUploadGetsA201() {
        var response = client.toBlocking().exchange(signedIn(HttpRequest.POST("/api/prototype/assets",
                Map.of("kind", "clip", "title", "Clip per HTTP", "tags", List.of(), "people", List.of(),
                        "durationSeconds", 45)), "EDITOR", "pool-create-status"), Asset.class);

        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(Asset.Stage.cut, response.body().stage());
    }

    @Test
    void anEditorReplacesTheTagsOfAnAssetTheyCreated() {
        String caller = "pool-tags-editor";
        Asset created = client.toBlocking().retrieve(signedIn(HttpRequest.POST("/api/prototype/assets",
                Map.of("kind", "raw", "title", "Tags per HTTP", "durationSeconds", 60)), "EDITOR", caller),
                Asset.class);

        Asset updated = client.toBlocking().retrieve(signedIn(HttpRequest.PUT("/api/prototype/assets/"
                + created.id() + "/tags", Map.of("tags", List.of("cilium"), "people", List.of())), "EDITOR", caller),
                Asset.class);

        assertEquals(List.of("cilium"), updated.tags());
    }

    private static MutableHttpRequest<?> viewer(MutableHttpRequest<?> request, String caller) {
        return signedIn(request, "VIEWER", caller);
    }

    private static MutableHttpRequest<?> signedIn(MutableHttpRequest<?> request, String roles, String caller) {
        return request.header("X-Test-Person", caller).header("X-Test-Roles", roles);
    }
}
