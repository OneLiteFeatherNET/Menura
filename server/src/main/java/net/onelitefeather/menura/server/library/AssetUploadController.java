package net.onelitefeather.menura.server.library;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Upload sessions of finished assets and raw material. Only the editor role may use them. The media bytes go from the
 * browser straight to the storage through the presigned part URLs; no route here accepts them.
 */
@Controller("/asset-upload-sessions")
public class AssetUploadController {

    private final AssetUploads uploads;
    private final IdentifierGenerator identifiers;

    public AssetUploadController(AssetUploads uploads, IdentifierGenerator identifiers) {
        this.uploads = uploads;
        this.identifiers = identifiers;
    }

    @RequiresRole(Role.EDITOR)
    @Post
    public HttpResponse<UploadSessionView> start(@Body AssetUploadRequest body, HttpRequest<?> request) throws SQLException {
        AssetDeclaration declaration = AssetDeclaration.declare(body.kind(), body.format(), body.durationSeconds(),
                body.width(), body.height(), body.sizeBytes(), body.checksum(), body.streamTitle(), body.streamDate());
        UploadSessionView view = uploads.start(person(request), declaration, RequestIds.from(request, identifiers));
        return HttpResponse.created(view).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @RequiresRole(Role.EDITOR)
    @Get("/{id}")
    public HttpResponse<UploadSessionView> read(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return noStore(uploads.read(person(request), sessionId(id)));
    }

    @RequiresRole(Role.EDITOR)
    @Post("/{id}/duplicate-confirmation")
    public HttpResponse<UploadSessionView> confirmDuplicate(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return noStore(uploads.confirmDuplicate(person(request), sessionId(id), RequestIds.from(request, identifiers)));
    }

    @RequiresRole(Role.EDITOR)
    @Post("/{id}/complete")
    public HttpResponse<UploadSessionView> complete(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return noStore(uploads.complete(person(request), sessionId(id), RequestIds.from(request, identifiers)));
    }

    @RequiresRole(Role.EDITOR)
    @Delete("/{id}")
    public HttpResponse<UploadSessionView> cancel(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return noStore(uploads.cancel(person(request), sessionId(id), RequestIds.from(request, identifiers)));
    }

    private static HttpResponse<UploadSessionView> noStore(UploadSessionView view) {
        return HttpResponse.ok(view).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }

    /** An identifier that is not a UUID names no session, so it answers 404 like an unknown one. */
    static UUID sessionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            throw new ProblemException(404, "Not Found", "No upload session with this id.");
        }
    }
}
