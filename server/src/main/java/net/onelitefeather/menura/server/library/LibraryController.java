package net.onelitefeather.menura.server.library;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The content library: the shared pool and its filters, free and person tags, clips, transcript search, transcripts,
 * the lineage of raw items, timelines and the registration of finished assets. Any signed-in person with a role may read;
 * every change needs the editor role. Media bytes never pass through these routes.
 */
@Controller("/library")
public class LibraryController {

    private final ContentLibrary library;
    private final Transcripts transcripts;
    private final Timelines timelines;
    private final IdentifierGenerator identifiers;

    public LibraryController(ContentLibrary library, Transcripts transcripts, Timelines timelines,
                             IdentifierGenerator identifiers) {
        this.library = library;
        this.transcripts = transcripts;
        this.timelines = timelines;
        this.identifiers = identifiers;
    }

    /** Body of a tag change: the free tags and the person tags that replace the current ones. */
    @Serdeable
    public record TagsBody(List<String> freeTags, List<UUID> personTags) {
    }

    /** Body of a clip. The status is {@code active} (the default) or {@code suggested}. */
    @Serdeable
    public record ClipBody(UUID rawItemId, Integer startSeconds, Integer endSeconds, String title, List<String> freeTags,
                           String status) {
    }

    @Serdeable
    public record ClipTitleBody(String title) {
    }

    @Serdeable
    public record TimelineBody(String title, TimelineDocument document) {
    }

    @Serdeable
    public record RegistrationBody(UUID timelineId, String title) {
    }

    @RequiresRole
    @Get("/items")
    public HttpResponse<ContentViews.PoolPageView> items(@Nullable @QueryValue Boolean mine, @Nullable @QueryValue String uploader,
                                                         @Nullable @QueryValue String person, @Nullable @QueryValue String tag,
                                                         @Nullable @QueryValue String stage, @Nullable @QueryValue String usage,
                                                         @Nullable @QueryValue LocalDate from, @Nullable @QueryValue LocalDate to,
                                                         @Nullable @QueryValue Integer limit,
                                                         @Nullable @QueryValue String cursor, HttpRequest<?> request)
            throws SQLException {
        ContentLibrary.PoolQuery query = new ContentLibrary.PoolQuery(Boolean.TRUE.equals(mine), uuid("uploader", uploader),
                uuid("person", person), tag, stage, usage, from, to);
        return HttpResponse.ok(library.pool(person(request), query, cursor, limit == null ? 50 : limit));
    }

    @RequiresRole(Role.EDITOR)
    @Put("/items/{id}/tags")
    public HttpResponse<ContentViews.ItemTagsView> tags(@PathVariable String id, @Body TagsBody body, HttpRequest<?> request)
            throws SQLException {
        Set<String> free = body.freeTags() == null ? Set.of() : Set.copyOf(body.freeTags());
        Set<UUID> persons = body.personTags() == null ? Set.of() : Set.copyOf(body.personTags());
        return HttpResponse.ok(library.setTags(person(request), itemId(id), free, persons, RequestIds.from(request, identifiers)));
    }

    @RequiresRole(Role.EDITOR)
    @Post("/clips")
    public HttpResponse<ContentViews.ClipView> createClip(@Body ClipBody body, HttpRequest<?> request) throws SQLException {
        Set<String> free = body.freeTags() == null ? Set.of() : Set.copyOf(body.freeTags());
        ContentLibrary.ClipRequest clip = new ContentLibrary.ClipRequest(body.rawItemId(), body.startSeconds(),
                body.endSeconds(), body.title(), free, body.status());
        return HttpResponse.created(library.createClip(person(request), clip, RequestIds.from(request, identifiers)));
    }

    @RequiresRole
    @Get("/clips/{id}")
    public HttpResponse<ContentViews.ClipView> clip(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(library.clip(person(request), itemId(id)));
    }

    @RequiresRole(Role.EDITOR)
    @Patch("/clips/{id}")
    public HttpResponse<ContentViews.ClipView> renameClip(@PathVariable String id, @Body ClipTitleBody body,
                                                          HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(library.renameClip(person(request), itemId(id), body.title(),
                RequestIds.from(request, identifiers)));
    }

    @RequiresRole(Role.EDITOR)
    @Delete("/clips/{id}")
    public HttpResponse<Void> deleteClip(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        library.deleteClip(person(request), itemId(id), RequestIds.from(request, identifiers));
        return HttpResponse.noContent();
    }

    @RequiresRole
    @Get("/search")
    public HttpResponse<List<ContentViews.SearchHitView>> search(@QueryValue String q, @Nullable @QueryValue Boolean mine,
                                                                 @Nullable @QueryValue String uploader,
                                                                 @Nullable @QueryValue String person,
                                                                 @Nullable @QueryValue String tag,
                                                                 @Nullable @QueryValue String stage,
                                                                 @Nullable @QueryValue String usage,
                                                                 @Nullable @QueryValue LocalDate from,
                                                                 @Nullable @QueryValue LocalDate to,
                                                                 @Nullable @QueryValue Integer limit, HttpRequest<?> request)
            throws SQLException {
        ContentLibrary.PoolQuery filter = new ContentLibrary.PoolQuery(Boolean.TRUE.equals(mine), uuid("uploader", uploader),
                uuid("person", person), tag, stage, usage, from, to);
        return HttpResponse.ok(library.search(person(request), q, filter, limit == null ? 20 : limit));
    }

    @RequiresRole
    @Get("/raw-items/{id}/lineage")
    public HttpResponse<ContentViews.RawLineageView> lineage(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(library.lineage(person(request), itemId(id)));
    }

    @RequiresRole(Role.EDITOR)
    @Put("/raw-items/{id}/transcript")
    @Consumes({"text/vtt", "application/x-subrip", "text/plain"})
    public HttpResponse<ContentViews.TranscriptView> transcript(@PathVariable String id, @Body String file,
                                                                HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(transcripts.upload(person(request), itemId(id), file, RequestIds.from(request, identifiers)))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @RequiresRole(Role.EDITOR)
    @Post("/timelines")
    public HttpResponse<ContentViews.TimelineView> storeTimeline(@Body TimelineBody body, HttpRequest<?> request)
            throws SQLException {
        return HttpResponse.created(timelines.store(person(request), body.title(), body.document(),
                RequestIds.from(request, identifiers)));
    }

    @RequiresRole
    @Get("/timelines/{id}")
    public HttpResponse<ContentViews.TimelineView> timeline(@PathVariable String id, HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(timelines.get(person(request), itemId(id)));
    }

    @RequiresRole(Role.EDITOR)
    @Post("/finished-assets/{id}/registration")
    public HttpResponse<ContentViews.RegisteredAssetView> register(@PathVariable String id, @Body RegistrationBody body,
                                                                   HttpRequest<?> request) throws SQLException {
        return HttpResponse.created(timelines.register(person(request), itemId(id), body.timelineId(), body.title(),
                RequestIds.from(request, identifiers)));
    }

    private static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }

    /** An identifier that is not a UUID names nothing, so it answers 404 like an unknown one. */
    static UUID itemId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            throw new ProblemException(404, "Not Found", "No item with this id.");
        }
    }

    private static UUID uuid(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            throw new ProblemException(400, "Invalid filter", "The field '" + field + "' must be an identifier.");
        }
    }
}
