package net.onelitefeather.menura.server.library;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.sql.SQLException;

/**
 * Finished assets and raw material: the listing, the one-asset read, signed preview and download links, and the
 * explicit deletion. Any signed-in person may list what they see; only the editor role deletes.
 */
@Controller("/assets")
public class AssetController {

    private final Assets assets;
    private final IdentifierGenerator identifiers;

    public AssetController(Assets assets, IdentifierGenerator identifiers) {
        this.assets = assets;
        this.identifiers = identifiers;
    }

    @RequiresRole
    @Get
    public HttpResponse<AssetPageView> list(@Nullable @QueryValue Integer limit, @Nullable @QueryValue String cursor,
                                            HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(assets.list(person(request), PageRequest.of(limit, cursor)));
    }

    @RequiresRole
    @Get("/{id}")
    public HttpResponse<AssetView> read(@PathVariable String id, HttpRequest<?> request) {
        return HttpResponse.ok(assets.get(person(request), assetId(id)));
    }

    @RequiresRole
    @Get("/{id}/preview-link")
    public HttpResponse<SignedLinkView> previewLink(@PathVariable String id, HttpRequest<?> request) {
        return link(assets.link(person(request), assetId(id), false));
    }

    @RequiresRole
    @Get("/{id}/download-link")
    public HttpResponse<SignedLinkView> downloadLink(@PathVariable String id, HttpRequest<?> request) {
        return link(assets.link(person(request), assetId(id), true));
    }

    @RequiresRole(Role.EDITOR)
    @Delete("/{id}")
    public HttpResponse<Void> delete(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        assets.delete(person(request), assetId(id), RequestIds.from(request, identifiers));
        return HttpResponse.noContent();
    }

    /** Links are credentials for one object: never cached. */
    private static HttpResponse<SignedLinkView> link(SignedLinkView view) {
        return HttpResponse.ok(view).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }

    /** An identifier that is not a UUID names no asset, so it answers 404 like an unknown one. */
    private static java.util.UUID assetId(String value) {
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            throw new ProblemException(404, "Not Found", "No asset with this id.");
        }
    }
}
