package net.onelitefeather.menura.server.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostService;
import net.onelitefeather.menura.core.post.ExternalPostStore.SyncStatus;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The representations of external posts. A removed post carries no link: the platform no longer offers it. */
public final class ExternalPostViews {

    private ExternalPostViews() {
    }

    public static ExternalPostPage page(ExternalPostService.Overview overview, List<ExternalPost> items, String nextCursor) {
        SyncStatus sync = overview.sync();
        return new ExternalPostPage(
                overview.channel().id(),
                overview.channel().state().value(),
                sync == null ? null : sync.lastSuccessAt(),
                sync == null ? null : sync.lastFailureAt(),
                sync == null ? null : sync.failureReason(),
                items.stream().map(ExternalPostViews::of).toList(),
                nextCursor);
    }

    public static ExternalPostView of(ExternalPost post) {
        return new ExternalPostView(post.id(), post.platform(), post.title(), post.format(), post.publishedAt(),
                post.removed() ? null : post.url(), post.removed() ? "removed" : "live", post.removedAt());
    }

    /**
     * One page of a channel's posts. lastSuccessfulSyncAt is null when the channel was never synced completely;
     * channelState is {@code needs_reconnection} while the posts are waiting for a reconnection.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExternalPostPage(UUID channelId, String channelState, Instant lastSuccessfulSyncAt,
                                   Instant lastFailedSyncAt, String syncFailureReason, List<ExternalPostView> items,
                                   String nextCursor) {
    }

    /** One external post. url is null when the post is removed. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ExternalPostView(UUID id, String platform, String title, String format, Instant publishedAt, String url,
                                   String status, Instant removedAt) {
    }
}
