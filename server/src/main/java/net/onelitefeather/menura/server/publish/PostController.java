package net.onelitefeather.menura.server.publish;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.publish.Content;
import net.onelitefeather.menura.core.publish.DryRunRefusal;
import net.onelitefeather.menura.core.publish.PostRevision;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.core.publish.PostService.DraftCommand;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.publish.PostViews.PostView;
import net.onelitefeather.menura.server.publish.PostViews.RevisionView;
import net.onelitefeather.menura.server.publish.PostViews.ProblemView;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Posts of the hub (change add-content-distribution): drafts, edits, review, approval, scheduling and revisions. Editors
 * and publishers create and change drafts; only a publisher approves, schedules and decides revisions, and approval and
 * the acceptance of a revision are refused outside the web application's session. No route here publishes.
 */
@Controller("/posts")
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @Post
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<PostView> create(HttpRequest<?> request, @Body DraftBody body) throws SQLException {
        return HttpResponse.created(PostViews.post(posts.create(PostActors.of(request), body.toCommand())));
    }

    @Get("/{id}")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView get(@PathVariable UUID id) throws SQLException {
        return PostViews.post(posts.get(id));
    }

    @Patch("/{id}")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView edit(HttpRequest<?> request, @PathVariable UUID id, @Body PatchBody body) throws SQLException {
        return PostViews.post(posts.edit(PostActors.of(request), id, body.toContent()));
    }

    @Delete("/{id}")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<Void> delete(HttpRequest<?> request, @PathVariable UUID id) throws SQLException {
        posts.delete(PostActors.of(request), id);
        return HttpResponse.noContent();
    }

    @Post("/{id}/submission")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView submit(HttpRequest<?> request, @PathVariable UUID id) throws SQLException {
        return PostViews.post(posts.submit(PostActors.of(request), id));
    }

    @Post("/{id}/approval")
    @RequiresRole({Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView approve(HttpRequest<?> request, @PathVariable UUID id) throws SQLException {
        return PostViews.post(posts.approve(PostActors.of(request), id));
    }

    @Post("/{id}/schedule")
    @RequiresRole({Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView schedule(HttpRequest<?> request, @PathVariable UUID id) throws SQLException {
        return PostViews.post(posts.schedule(PostActors.of(request), id));
    }

    /** The made-for-kids declaration of a YouTube post; scheduling is refused until it is given (add-youtube-integration). */
    @Put("/{id}/youtube-declaration")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView declareMadeForKids(HttpRequest<?> request, @PathVariable UUID id, @Body MadeForKidsBody body)
            throws SQLException {
        return PostViews.post(posts.declareMadeForKids(PostActors.of(request), id, body.madeForKids()));
    }

    @Get("/{id}/revisions")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    List<RevisionView> revisions(@PathVariable UUID id) throws SQLException {
        return posts.revisions(id).stream().map(PostViews::revision).toList();
    }

    @Post("/{id}/revisions")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<RevisionView> propose(HttpRequest<?> request, @PathVariable UUID id, @Body RevisionBody body)
            throws SQLException {
        PostRevision revision = posts.propose(PostActors.of(request), id, body.toContent(), body.rationale());
        return HttpResponse.created(PostViews.revision(revision));
    }

    @Post("/{id}/revisions/{revisionId}/acceptance")
    @RequiresRole({Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    PostView accept(HttpRequest<?> request, @PathVariable UUID id, @PathVariable UUID revisionId) throws SQLException {
        return PostViews.post(posts.acceptRevision(PostActors.of(request), id, revisionId));
    }

    @Post("/{id}/revisions/{revisionId}/rejection")
    @RequiresRole({Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    RevisionView reject(HttpRequest<?> request, @PathVariable UUID id, @PathVariable UUID revisionId) throws SQLException {
        return PostViews.revision(posts.rejectRevision(PostActors.of(request), id, revisionId));
    }

    /** A scheduling refused by the dry run: 422 Problem Details that lists the blocking problems. */
    @Error(exception = DryRunRefusal.class)
    HttpResponse<ScheduleRefusalView> refused(HttpRequest<?> request, DryRunRefusal refusal) {
        List<ProblemView> problems = PostViews.problems(refusal.problems());
        return HttpResponse.status(io.micronaut.http.HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.of("application/problem+json"))
                .body(new ScheduleRefusalView("about:blank", "Unprocessable Entity", 422, refusal.detail(),
                        request.getPath(), problems));
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ScheduleRefusalView(String type, String title, int status, String detail, String instance,
                                      List<ProblemView> problems) {
    }

    /** The body of a new draft. Fields not given are filled from the series when one is named. */
    @Serdeable
    public record DraftBody(UUID channelId, UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId, String title,
                            String description, List<String> hashtags, String visibility, Instant scheduledAt) {

        DraftCommand toCommand() {
            Visibility parsed = PostActors.visibility(visibility);
            return new DraftCommand(channelId, assetId, coverAssetId, seriesId, sourcePostId, title, description, hashtags,
                    parsed, scheduledAt);
        }
    }

    /** The body of an edit: only the fields given change. */
    @Serdeable
    public record MadeForKidsBody(boolean madeForKids) {
    }

    public record PatchBody(UUID channelId, UUID coverAssetId, String title, String description, List<String> hashtags,
                            String visibility, Instant scheduledAt) {

        Content toContent() {
            return new Content(channelId, coverAssetId, title, description, hashtags, PostActors.visibility(visibility),
                    scheduledAt);
        }
    }

    /** The body of a revision: the fields it proposes, and the rationale. */
    @Serdeable
    public record RevisionBody(String rationale, UUID channelId, UUID coverAssetId, String title, String description,
                               List<String> hashtags, Instant scheduledAt) {

        Content toContent() {
            return new Content(channelId, coverAssetId, title, description, hashtags, null, scheduledAt);
        }
    }
}
