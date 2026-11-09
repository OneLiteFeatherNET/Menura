package net.onelitefeather.menura.server.library;

import io.micronaut.core.type.Argument;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks the route table of the server against an allowlist (principle 1: the server never receives media bytes). A new
 * route must be added to the allowlist on purpose, and no route may take a binary or multipart body.
 */
@MicronautTest
class RouteTableTest {

    /** Every route of the server, as "METHOD template". Adding a route changes this list on purpose. */
    static final Set<String> ALLOWED = Set.of(
            "GET /health",
            "GET /auth/login",
            "GET /auth/callback",
            "GET /auth/session",
            "POST /auth/logout",
            "POST /calendar-feed-token",
            "DELETE /calendar-feed-token",
            "GET /calendar/feed/{token}",
            "GET /calendar",
            "POST /posts",
            "GET /posts/{id}",
            "PATCH /posts/{id}",
            "DELETE /posts/{id}",
            "POST /posts/{id}/submission",
            "POST /posts/{id}/approval",
            "POST /posts/{id}/schedule",
            "GET /posts/{id}/revisions",
            "POST /posts/{id}/revisions",
            "POST /posts/{id}/revisions/{revisionId}/acceptance",
            "POST /posts/{id}/revisions/{revisionId}/rejection",
            "POST /post-plans",
            "POST /series/{id}/episodes",
            "GET /openapi/menura.yaml",
            "POST /asset-upload-sessions",
            "GET /asset-upload-sessions/{id}",
            "POST /asset-upload-sessions/{id}/duplicate-confirmation",
            "POST /asset-upload-sessions/{id}/complete",
            "DELETE /asset-upload-sessions/{id}",
            "GET /assets",
            "GET /assets/{id}",
            "GET /assets/{id}/preview-link",
            "GET /assets/{id}/download-link",
            "DELETE /assets/{id}",
            "GET /platforms",
            "PUT /platforms/{platform}/audit-status",
            "GET /channels",
            "GET /channels/{id}",
            "POST /channels/connections",
            "POST /channels/{id}/consent",
            "DELETE /channels/{id}",
            "GET /channels/{id}/external-posts",
            "GET /channel-connections/callback",
            "GET /brand-kit",
            "GET /brand-kit/versions/{version}",
            "POST /brand-kit/versions",
            "GET /brand-kit/render-bundle",
            "GET /brand-kit/css",
            "GET /brand-kit/templates/{format}",
            "GET /brand-kit/texts/{language}",
            "GET /brand-kit/pins/{consumer}",
            "PUT /brand-kit/pins/{consumer}",
            "DELETE /brand-kit/pins/{consumer}",
            "GET /brand-kit/events",
            "GET /library/items",
            "PUT /library/items/{id}/tags",
            "POST /library/clips",
            "GET /library/clips/{id}",
            "PATCH /library/clips/{id}",
            "DELETE /library/clips/{id}",
            "GET /library/search",
            "GET /library/raw-items/{id}/lineage",
            "PUT /library/raw-items/{id}/transcript",
            "DELETE /library/raw-items/{id}/media",
            "POST /library/timelines",
            "GET /library/timelines/{id}",
            "POST /library/finished-assets/{id}/registration",
            "GET /series",
            "POST /series",
            "GET /series/overview",
            "GET /series/{id}",
            "PUT /series/{id}",
            "PUT /external-posts/{id}/series",
            "GET /channels/{id}/series-render-bundle",
            "GET /analytics/overview",
            "GET /analytics/series",
            "GET /analytics/equal-age",
            "GET /analytics/external-posts/{id}",
            "GET /analytics/external-posts/{id}/points",
            "POST /join-events",
            "POST /join-events/{eventId}/source-answer",
            "GET /join-attribution/settings",
            "PUT /join-attribution/settings",
            "GET /join-attribution/new-players",
            "GET /join-attribution/posts/{id}",
            "GET /join-attribution/series",
            "PUT /posts/{id}/youtube-declaration",
            "GET /channels/{id}/quota");

    private static final Set<Class<?>> BINARY_BODIES = Set.of(byte[].class, InputStream.class, ByteBuffer.class,
            StreamingFileUpload.class, CompletedFileUpload.class);

    @Inject
    Router router;

    @Test
    void noRouteTakesMediaBytesAsItsBody() {
        List<String> binary = routes().stream()
                .filter(route -> route.getRequestBodyType().map(Argument::getType).map(BINARY_BODIES::contains).orElse(false))
                .map(route -> route.getHttpMethodName() + " " + route.getUriMatchTemplate())
                .toList();

        assertEquals(List.of(), binary, "no route may accept a binary or multipart body");
    }

    @Test
    void routeTableMatchesTheAllowlist() {
        Set<String> actual = routes().stream()
                .map(route -> route.getHttpMethodName() + " " + route.getUriMatchTemplate())
                .collect(Collectors.toSet());

        assertFalse(actual.isEmpty(), "the server must expose its routes");
        assertEquals(ALLOWED, actual, "the route table differs from the allowlist");
    }

    /**
     * The production routes: HEAD routes are implied by GET, and the /test-* controllers exist only in the test sources.
     */
    private List<UriRouteInfo<?, ?>> routes() {
        return router.uriRoutes()
                .filter(route -> !route.getHttpMethodName().equals("HEAD"))
                .filter(route -> !route.getUriMatchTemplate().toString().startsWith("/test-"))
                .toList();
    }
}
