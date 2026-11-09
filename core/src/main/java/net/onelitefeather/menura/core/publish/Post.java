package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A finished asset planned onto one channel: the per-channel text, hashtags, cover and time, its status and the record of
 * its publication. Every change returns a new record and takes its time from the caller's injected clock. Transitions
 * that the status table refuses throw a 409 Problem Details error, and nothing changes.
 *
 * <p>The text is copied into the post when it is planned (series defaults included), so a later change to a series never
 * reaches a planned post. The lease fields belong to the publication job: a claimed post is not claimed again until its
 * lease expires.
 */
public record Post(UUID id, UUID channelId, UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId,
                   UUID authorId, PostStatus status, String title, String description, List<String> hashtags,
                   Visibility visibility, String playlistId, Instant scheduledAt, UUID approvedBy, Instant approvedAt,
                   Instant handedOverAt, String externalId, String externalUrl, Instant publishedAt, String failureReason,
                   int attempts, Instant nextAttemptAt, String leaseOwner, Instant leaseUntil, int revision,
                   Instant createdAt, Instant updatedAt) {

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 5000;
    static final int HASHTAGS_MAX = 30;
    static final int REASON_MAX = 500;
    private static final Pattern HASHTAG = Pattern.compile("#?[\\p{L}\\p{N}_]{1,50}");

    public Post {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        hashtags = List.copyOf(Objects.requireNonNull(hashtags, "hashtags"));
        description = description == null ? "" : description;
    }

    /**
     * A new draft written by {@code authorId}. The text is checked against the storage limits; platform limits are checked
     * by the dry run, which may report problems on a draft.
     */
    public static Post draft(UUID id, UUID authorId, PostDraft draft, Instant now) {
        Objects.requireNonNull(draft, "draft");
        String title = title(draft.title());
        return new Post(id, draft.channelId(), draft.assetId(), draft.coverAssetId(), draft.seriesId(),
                draft.sourcePostId(), authorId, PostStatus.DRAFT, title, description(draft.description()),
                hashtags(draft.hashtags()), draft.visibility() == null ? Visibility.PRIVATE : draft.visibility(),
                draft.playlistId(), draft.scheduledAt(), null, null, null, null, null, null, null, 0, null, null, null,
                0, now, now);
    }

    /** The post with the given fields changed. A null field of the content is unchanged. The status does not change. */
    public Post edited(Content content, String playlistId, Instant at) {
        Objects.requireNonNull(content, "content");
        if (!status.editable()) {
            throw conflict("A post in status " + status.value() + " is not edited; propose a revision instead.");
        }
        return apply(content, playlistId, at);
    }

    /** The post with a revision accepted: content changed, and an approved, scheduled or failed post returns to review. */
    public Post revised(Content content, String playlistId, Instant at) {
        Objects.requireNonNull(content, "content");
        Post changed = apply(content, playlistId, at);
        if (status == PostStatus.DRAFT || status == PostStatus.IN_REVIEW) {
            return changed;
        }
        return changed.reentered(at);
    }

    public Post submitted(Instant at) {
        return moved(PostStatus.IN_REVIEW, at);
    }

    /** A publisher sends an in-review post back to draft. */
    public Post returned(Instant at) {
        return moved(PostStatus.DRAFT, at);
    }

    public Post approved(UUID by, Instant at) {
        Post next = moved(PostStatus.APPROVED, at);
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, next.status, title,
                description, hashtags, visibility, playlistId, scheduledAt, by, at, handedOverAt, externalId, externalUrl,
                publishedAt, failureReason, attempts, nextAttemptAt, leaseOwner, leaseUntil, revision, createdAt, at);
    }

    /** A post on the calendar that is due or handed over. An approved or failed post becomes scheduled and starts over. */
    public Post scheduled(Instant at) {
        if (scheduledAt == null) {
            throw new ProblemException(422, "Unprocessable Entity", "A post needs a scheduled time before it is scheduled.");
        }
        Post next = moved(PostStatus.SCHEDULED, at);
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, next.status, title,
                description, hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt,
                externalId, externalUrl, null, null, 0, null, null, null, revision, createdAt, at);
    }

    /** The platform accepted the post for its own timed publication. The post stays scheduled until the time arrives. */
    public Post handedOver(String externalId, String url, Instant at) {
        requireStatus(PostStatus.SCHEDULED, "hand over");
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, at, externalId, url, null,
                failureReason, attempts, nextAttemptAt, leaseOwner, leaseUntil, revision, createdAt, at);
    }

    /** A stepwise upload produced the platform item: its identifier and URL are kept while the post stays scheduled. */
    public Post uploaded(String externalId, String url, Instant at) {
        requireStatus(PostStatus.SCHEDULED, "record an upload");
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, url, null,
                failureReason, attempts, nextAttemptAt, leaseOwner, leaseUntil, revision, createdAt, at);
    }

    public Post published(String externalId, String url, Instant publishedAt, Instant at) {
        Post next = moved(PostStatus.PUBLISHED, at);
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, next.status, title,
                description, hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt,
                externalId, url, publishedAt, null, attempts, null, null, null, revision, createdAt, at);
    }

    /** The post failed for good. Its reason is shown with its attempt count until a person schedules it again. */
    public Post failed(String reason, Instant at) {
        Post next = moved(PostStatus.FAILED, at);
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, next.status, title,
                description, hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt,
                externalId, externalUrl, publishedAt, reason(reason), attempts, null, null, null, revision, createdAt, at);
    }

    /** A transient failure: the post stays scheduled and is due again at {@code next}. */
    public Post retryAt(String reason, Instant next, Instant at) {
        requireStatus(PostStatus.SCHEDULED, "retry");
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, externalUrl,
                publishedAt, reason(reason), attempts, next, null, null, revision, createdAt, at);
    }

    /** A job claims the post for one run until {@code until}. */
    public Post claimed(String owner, Instant until, Instant at) {
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, externalUrl,
                publishedAt, failureReason, attempts, nextAttemptAt, owner, until, revision, createdAt, at);
    }

    /** The post with its lease released. */
    public Post released(Instant at) {
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, externalUrl,
                publishedAt, failureReason, attempts, nextAttemptAt, null, null, revision, createdAt, at);
    }

    /** A publication attempt starts: the attempt counter goes up by one. */
    public Post attemptStarted(Instant at) {
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, externalUrl,
                publishedAt, failureReason, attempts + 1, nextAttemptAt, leaseOwner, leaseUntil, revision, createdAt, at);
    }

    /** The same post with the revision the store gave it when it was written. */
    public Post withRevision(int stored) {
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, status, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, externalUrl,
                publishedAt, failureReason, attempts, nextAttemptAt, leaseOwner, leaseUntil, stored, createdAt, updatedAt);
    }

    public boolean leased(Instant now) {
        return leaseUntil != null && leaseUntil.isAfter(now);
    }

    /** The content of this post as an edit can read it. */
    public Content content() {
        return new Content(channelId, coverAssetId, title, description, hashtags, visibility, scheduledAt);
    }

    /** Whether the post has been handed to the platform for timed publication (native scheduling). */
    public boolean handedOver() {
        return handedOverAt != null;
    }

    private Post reentered(Instant at) {
        Post next = moved(PostStatus.IN_REVIEW, at);
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, next.status, title,
                description, hashtags, visibility, playlistId, scheduledAt, null, null, handedOverAt, externalId,
                externalUrl, null, null, 0, null, null, null, revision, createdAt, at);
    }

    private Post apply(Content content, String playlistId, Instant at) {
        UUID channel = content.channelId() == null ? channelId : content.channelId();
        String nextTitle = content.title() == null ? title : title(content.title());
        String nextDescription = content.description() == null ? description : description(content.description());
        List<String> nextHashtags = content.hashtags() == null ? hashtags : hashtags(content.hashtags());
        Visibility nextVisibility = content.visibility() == null ? visibility : content.visibility();
        Instant nextTime = content.scheduledAt() == null ? scheduledAt : content.scheduledAt();
        UUID nextCover = content.coverAssetId() == null ? coverAssetId : content.coverAssetId();
        boolean otherChannel = !channel.equals(channelId);
        String nextPlaylist = otherChannel ? playlistId : (playlistId == null ? this.playlistId : playlistId);
        return new Post(id, channel, assetId, nextCover, seriesId, sourcePostId, authorId, status, nextTitle,
                nextDescription, nextHashtags, nextVisibility, nextPlaylist, nextTime, approvedBy, approvedAt, handedOverAt,
                externalId, externalUrl, publishedAt, failureReason, attempts, nextAttemptAt, leaseOwner, leaseUntil,
                revision, createdAt, at);
    }

    private Post moved(PostStatus target, Instant at) {
        if (!status.canMoveTo(target)) {
            throw conflict("A post in status " + status.value() + " cannot move to " + target.value() + ".");
        }
        return new Post(id, channelId, assetId, coverAssetId, seriesId, sourcePostId, authorId, target, title, description,
                hashtags, visibility, playlistId, scheduledAt, approvedBy, approvedAt, handedOverAt, externalId, externalUrl,
                publishedAt, failureReason, attempts, nextAttemptAt, leaseOwner, leaseUntil, revision, createdAt, at);
    }

    private void requireStatus(PostStatus expected, String action) {
        if (status != expected) {
            throw conflict("A post in status " + status.value() + " cannot " + action + ".");
        }
    }

    static ProblemException conflict(String detail) {
        return new ProblemException(409, "Conflict", detail);
    }

    static String title(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("The title is required.");
        }
        String trimmed = value.strip();
        if (trimmed.length() > TITLE_MAX) {
            throw invalid("The title is longer than " + TITLE_MAX + " characters.");
        }
        return trimmed;
    }

    static String description(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() > DESCRIPTION_MAX) {
            throw invalid("The description is longer than " + DESCRIPTION_MAX + " characters.");
        }
        return value;
    }

    /** Hashtags with a leading '#', without duplicates, in the order given. */
    static List<String> hashtags(List<String> values) {
        if (values.size() > HASHTAGS_MAX) {
            throw invalid("At most " + HASHTAGS_MAX + " hashtags can be given.");
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || !HASHTAG.matcher(value.strip()).matches()) {
                throw invalid("A hashtag may contain letters, digits and underscores only.");
            }
            String tag = value.strip().startsWith("#") ? value.strip() : "#" + value.strip();
            if (!out.contains(tag)) {
                out.add(tag);
            }
        }
        return List.copyOf(out);
    }

    private static String reason(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > REASON_MAX ? value.substring(0, REASON_MAX) : value;
    }

    static ProblemException invalid(String detail) {
        return new ProblemException(400, "Bad Request", detail);
    }
}
