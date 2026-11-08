package net.onelitefeather.menura.server.publish;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.core.publish.PostService.EpisodeCommand;
import net.onelitefeather.menura.core.publish.PostService.PlanCommand;
import net.onelitefeather.menura.core.publish.PostService.PlanItem;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.publish.PostViews.EpisodeResultView;
import net.onelitefeather.menura.server.publish.PostViews.PlanResultView;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Planning (change add-content-distribution): one finished asset onto several channels, and the next episodes of a series
 * on the free slots of one channel. Both create drafts only; the drafts go through review and approval like any other.
 */
@Controller
public class PostPlanController {

    static final int MAX_CHANNELS = 20;

    private final PostService posts;

    public PostPlanController(PostService posts) {
        this.posts = posts;
    }

    @Post("/post-plans")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<PlanResultView> planChannels(HttpRequest<?> request, @Body PlanBody body) throws SQLException {
        if (body == null || body.channels() == null || body.channels().isEmpty()) {
            throw new ProblemException(400, "Bad Request", "A plan needs at least one channel.");
        }
        if (body.channels().size() > MAX_CHANNELS) {
            throw new ProblemException(400, "Bad Request", "At most " + MAX_CHANNELS + " channels can be planned at once.");
        }
        List<PlanItem> items = body.channels().stream().map(item -> new PlanItem(item.channelId(), item.scheduledAt(),
                item.title(), item.description(), item.hashtags(), PostActors.visibility(item.visibility()))).toList();
        PostService.PlanResult result = posts.planChannels(PostActors.of(request),
                new PlanCommand(body.assetId(), body.coverAssetId(), body.seriesId(), body.sourcePostId(), items));
        return HttpResponse.created(PostViews.plan(result));
    }

    @Post("/series/{id}/episodes")
    @RequiresRole({Role.EDITOR, Role.PUBLISHER})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<EpisodeResultView> planEpisodes(HttpRequest<?> request, @PathVariable UUID id, @Body EpisodeBody body)
            throws SQLException {
        if (body == null || body.count() == null) {
            throw new ProblemException(400, "Bad Request", "The number of episodes is required.");
        }
        PostService.EpisodeResult result = posts.planEpisodes(PostActors.of(request), id,
                new EpisodeCommand(body.channelId(), body.assetId(), body.coverAssetId(), body.count()));
        return HttpResponse.created(PostViews.episodes(result));
    }

    @Serdeable
    public record PlanBody(UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId, List<PlanItemBody> channels) {
    }

    @Serdeable
    public record PlanItemBody(UUID channelId, Instant scheduledAt, String title, String description, List<String> hashtags,
                               String visibility) {
    }

    @Serdeable
    public record EpisodeBody(UUID channelId, UUID assetId, UUID coverAssetId, Integer count) {
    }
}
