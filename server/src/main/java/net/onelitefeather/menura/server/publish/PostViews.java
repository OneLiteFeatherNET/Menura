package net.onelitefeather.menura.server.publish;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.publish.AttemptRecord;
import net.onelitefeather.menura.core.publish.CalendarRange;
import net.onelitefeather.menura.core.publish.DryRun;
import net.onelitefeather.menura.core.publish.Post;
import net.onelitefeather.menura.core.publish.PostRevision;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.core.publish.PostService.PostDetail;
import net.onelitefeather.menura.core.publish.StepRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * The representations of posts, revisions, plans and the calendar. Every response record serializes its null members
 * (JsonInclude.ALWAYS), so a client reads each member the contract declares. No view carries a credential: the channel
 * is shown by its name and platform only.
 */
public final class PostViews {

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private PostViews() {
    }

    public static PostView post(PostDetail detail) {
        Post post = detail.post();
        Channel channel = detail.channel();
        return new PostView(post.id(), post.channelId(), channel.displayName(), channel.platform(), channel.state().value(),
                post.assetId(), post.coverAssetId(), post.seriesId(), post.sourcePostId(), post.authorId(),
                post.status().value(), post.title(), post.description(), post.hashtags(), visibility(post),
                post.playlistId(), post.scheduledAt(), post.handedOverAt(), post.approvedBy(), post.approvedAt(),
                post.publishedAt(), post.externalUrl(), post.externalId(), post.failureReason(), post.attempts(),
                post.nextAttemptAt(), post.revision(), steps(detail.steps()), attempts(detail.attempts()),
                problems(detail.problems()), post.createdAt(), post.updatedAt());
    }

    /** A calendar item: the post, with its time in the installation time zone. */
    public static CalendarItemView item(PostDetail detail, ZoneId zone) {
        PostView view = post(detail);
        Instant startsAt = detail.post().publishedAt() != null ? detail.post().publishedAt() : detail.post().scheduledAt();
        ZonedDateTime local = startsAt.atZone(zone);
        return new CalendarItemView(view.id(), view.channelId(), view.channelName(), view.platform(), view.channelState(),
                view.assetId(), view.coverAssetId(), view.seriesId(), view.sourcePostId(), view.authorId(), view.status(),
                view.title(), view.description(), view.hashtags(), view.visibility(), view.playlistId(), view.scheduledAt(),
                view.handedOverAt(), view.approvedBy(), view.approvedAt(), view.publishedAt(), view.url(), view.externalId(),
                view.failureReason(), view.attempts(), view.nextAttemptAt(), view.revision(), view.steps(),
                view.attemptHistory(), view.problems(), view.createdAt(), view.updatedAt(), startsAt,
                local.toLocalDate().toString(), local.format(LOCAL_TIME));
    }

    public static RevisionView revision(PostRevision revision) {
        var content = revision.content();
        return new RevisionView(revision.id(), revision.postId(), revision.authorId(), revision.rationale(),
                revision.status().value(), content.title(), content.description(), content.hashtags(), content.coverAssetId(),
                content.scheduledAt(), content.channelId(), revision.decidedBy(), revision.decidedAt(), revision.createdAt());
    }

    public static PlanResultView plan(PostService.PlanResult result) {
        return new PlanResultView(result.posts().stream().map(PostViews::post).toList(),
                result.refused().stream().map(refusal -> new RefusalView(refusal.channelId(), refusal.reason())).toList());
    }

    public static EpisodeResultView episodes(PostService.EpisodeResult result) {
        return new EpisodeResultView(result.posts().stream().map(PostViews::post).toList(),
                result.skipped().stream().map(skip -> new SkippedView(skip.scheduledAt(), skip.postId(), skip.reason()))
                        .toList());
    }

    public static CalendarPageView calendar(CalendarRange range, PostService.CalendarPage page) {
        return new CalendarPageView(range.zone().getId(), range.view().value(), range.firstDay().toString(),
                range.lastDay().toString(), range.from(), range.to(), page.truncated(),
                page.items().stream().map(item -> item(item, range.zone())).toList());
    }

    static String visibility(Post post) {
        return post.visibility().name().toLowerCase(java.util.Locale.ROOT);
    }

    static List<StepView> steps(List<StepRecord> steps) {
        return steps.stream().map(step -> new StepView(step.step().value(), step.state().value(), step.changedAt(),
                step.reason())).toList();
    }

    static List<AttemptView> attempts(List<AttemptRecord> attempts) {
        return attempts.stream().map(attempt -> new AttemptView(attempt.attempt(), attempt.startedAt(), attempt.finishedAt(),
                attempt.outcome(), attempt.reason())).toList();
    }

    static List<ProblemView> problems(List<DryRun.Problem> problems) {
        return problems.stream().map(problem -> new ProblemView(problem.code(), problem.severity(), problem.message(),
                problem.action())).toList();
    }

    /** One post. Its steps and attempt history are its publication record; its problems are the dry run on read. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PostView(UUID id, UUID channelId, String channelName, String platform, String channelState, UUID assetId,
                           UUID coverAssetId, UUID seriesId, UUID sourcePostId, UUID authorId, String status, String title,
                           String description, List<String> hashtags, String visibility, String playlistId, Instant scheduledAt,
                           Instant handedOverAt, UUID approvedBy, Instant approvedAt, Instant publishedAt, String url,
                           String externalId, String failureReason, int attempts, Instant nextAttemptAt, int revision,
                           List<StepView> steps, List<AttemptView> attemptHistory, List<ProblemView> problems,
                           Instant createdAt, Instant updatedAt) {
    }

    /** One calendar item: a post with its start instant and its local date and time. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CalendarItemView(UUID id, UUID channelId, String channelName, String platform, String channelState,
                                   UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId, UUID authorId,
                                   String status, String title, String description, List<String> hashtags, String visibility,
                                   String playlistId, Instant scheduledAt, Instant handedOverAt, UUID approvedBy,
                                   Instant approvedAt, Instant publishedAt, String url, String externalId,
                                   String failureReason, int attempts, Instant nextAttemptAt, int revision,
                                   List<StepView> steps, List<AttemptView> attemptHistory, List<ProblemView> problems,
                                   Instant createdAt, Instant updatedAt, Instant startsAt, String localDate, String localTime) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StepView(String step, String state, Instant changedAt, String reason) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AttemptView(int attempt, Instant startedAt, Instant finishedAt, String outcome, String reason) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProblemView(String code, String severity, String message, String action) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RevisionView(UUID id, UUID postId, UUID authorId, String rationale, String status, String title,
                               String description, List<String> hashtags, UUID coverAssetId, Instant scheduledAt,
                               UUID channelId, UUID decidedBy, Instant decidedAt, Instant createdAt) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlanResultView(List<PostView> posts, List<RefusalView> refused) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RefusalView(UUID channelId, String reason) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EpisodeResultView(List<PostView> posts, List<SkippedView> skipped) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SkippedView(Instant scheduledAt, UUID postId, String reason) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CalendarPageView(String timeZone, String view, String firstDay, String lastDay, Instant from, Instant to,
                                   boolean truncated, List<CalendarItemView> items) {
    }
}
