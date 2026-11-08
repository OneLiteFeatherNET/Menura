package net.onelitefeather.menura.server.library;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor-only rule of the upload routes, over HTTP, with in-memory storage and repositories. */
@MicronautTest(rebuildContext = true)
class AssetHttpTest {

    private static final String EDITOR = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final long SIZE = 3_000_000_000L;
    /** 3 GB in parts of 95 MB is 31 parts. */
    private static final String CHECKSUM = "A".repeat(43) + "=-31";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryLibraryRepositories.Sessions sessions;

    @Test
    void viewerIsRefusedWith403AndNoSessionIsCreated() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(HttpRequest.POST("/asset-upload-sessions", videoBody()),
                        EDITOR, "VIEWER")));

        assertEquals(403, refused.getStatus().getCode(), "a viewer may not start an upload");
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
        assertTrue(sessions.rows().isEmpty(), "a refused request creates no session");
    }

    @Test
    void anonymousCallerIsRefusedWith401() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.POST("/asset-upload-sessions", videoBody())));

        assertEquals(401, refused.getStatus().getCode());
    }

    @Test
    void editorStartsAnUploadAndGetsPresignedPartUrlsWithNoStoreCache() {
        HttpResponse<Map> response = client.toBlocking().exchange(
                withPerson(HttpRequest.POST("/asset-upload-sessions", videoBody()), EDITOR, "EDITOR"), Map.class);

        assertEquals(201, response.code(), "starting an upload answers 201 Created");
        assertEquals("no-store", response.header("Cache-Control"), "presigned URLs are never cached");
        assertEquals("uploading", response.getBody(Map.class).orElseThrow().get("state"));
        assertEquals(1, sessions.rows().size(), "the editor's upload creates one session");
    }

    @Test
    void invalidDeclarationIsRefusedNamingTheMissingField() {
        Map<String, Object> body = new java.util.HashMap<>(videoBody());
        body.remove("width");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(HttpRequest.POST("/asset-upload-sessions", body), EDITOR, "EDITOR")));

        assertEquals(400, refused.getStatus().getCode());
        String detail = refused.getResponse().getBody(String.class).orElse("");
        assertTrue(detail.contains("width"), "the Problem Details name the missing field: " + detail);
        assertTrue(sessions.rows().isEmpty(), "an invalid declaration creates no session");
    }

    @Test
    void viewerMayListAssets() {
        HttpResponse<String> response = client.toBlocking().exchange(
                withPerson(HttpRequest.GET("/assets"), EDITOR, "VIEWER"), String.class);

        assertEquals(200, response.code(), "any signed-in person may list the visible assets");
        assertTrue(response.body().contains("\"items\""), "the listing is a cursor page: " + response.body());
    }

    private static Map<String, Object> videoBody() {
        return Map.of("kind", "video", "format", "mp4", "durationSeconds", 95, "width", 1920, "height", 1080,
                "sizeBytes", SIZE, "checksum", CHECKSUM);
    }

    private static MutableHttpRequest<?> withPerson(MutableHttpRequest<?> request, String person, String roles) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person).header(HeaderIdentityFilter.ROLES_HEADER, roles);
    }
}
