package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.ContentSeries;
import net.onelitefeather.menura.core.series.SeriesService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The posts of the hub (change add-content-distribution): drafts, planning onto one or several channels, the next
 * episodes of a series on the free slots, approval, scheduling, revisions and the calendar read. Every rule that a client
 * could skip is enforced here: a status skip, an approval without the publisher role or outside the web application, and
 * a schedule that the dry run blocks are refused whichever client asks.
 */
public final class PostService {

    /** The most calendar items one read returns; a wider window reports itself as truncated. */
    public static final int CALENDAR_LIMIT = 1000;
    /** The most episodes one planning call creates. */
    public static final int EPISODES_MAX = 10;
    /** How far the episode planner looks for free slots, in weeks, beyond the number of episodes. */
    static final int SEARCH_WEEKS = 52;
    /** The window of the calendar feed: planned posts up to this many days ahead. */
    public static final int FEED_DAYS = 366;

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final PostStore posts;
    private final ChannelStore channels;
    private final SeriesService series;
    private final PlatformRegistry platforms;
    private final ConnectionService connections;
    private final ZoneId zone;

    public PostService(Transactions transactions, Clock clock, IdentifierGenerator identifiers, PostStore posts,
                       ChannelStore channels, SeriesService series, PlatformRegistry platforms,
                       ConnectionService connections, ZoneId zone) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.posts = posts;
        this.channels = channels;
        this.series = series;
        this.platforms = platforms;
        this.connections = connections;
        this.zone = zone;
    }

    /** A post with what its page or calendar item shows: its channel, the asset kind, steps, attempts and problems. */
    public record PostDetail(Post post, Channel channel, String assetKind, List<StepRecord> steps,
                             List<AttemptRecord> attempts, List<DryRun.Problem> problems, YouTubeDeclaration declaration,
                             PostUpload upload) {
    }

    /** One channel of a multi-channel plan, with its own text and time. */
    public record PlanItem(UUID channelId, Instant scheduledAt, String title, String description, List<String> hashtags,
                           Visibility visibility) {
    }

    public record PlanCommand(UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId, List<PlanItem> items) {
    }

    /** A channel the plan could not use, with the reason in the fixed words of the service. */
    public record Refusal(UUID channelId, String reason) {
    }

    public record PlanResult(List<PostDetail> posts, List<Refusal> refused) {
    }

    public record EpisodeCommand(UUID channelId, UUID assetId, UUID coverAssetId, int count) {
    }

    /** A slot the episode planner skipped because a post on the channel already holds it. */
    public record SkippedSlot(Instant scheduledAt, UUID postId, String reason) {
    }

    public record EpisodeResult(List<PostDetail> posts, List<SkippedSlot> skipped) {
    }

    public record DraftCommand(UUID channelId, UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId,
                               String title, String description, List<String> hashtags, Visibility visibility,
                               Instant scheduledAt) {
    }

    /** A calendar window: its posts, and whether the window held more than the limit. */
    public record CalendarPage(List<PostDetail> items, boolean truncated) {
    }

    // ------------------------------------------------------------------ drafts

    public PostDetail get(UUID id) throws SQLException {
        return transactions.inTransaction(c -> detail(c, requirePost(c, id), true));
    }

    /**
     * A draft on one channel. Fields not given are filled from the series defaults when the draft belongs to a series. A
     * recycled post (with a source) must reuse the asset of its source, which stays unchanged.
     */
    public PostDetail create(PostActor actor, DraftCommand command) throws SQLException {
        requireEditor(actor);
        return transactions.inTransaction(c -> {
            Post post = draft(c, actor, command.channelId(), command.assetId(), command.coverAssetId(), command.seriesId(),
                    command.sourcePostId(), command.title(), command.description(), command.hashtags(),
                    command.visibility(), command.scheduledAt());
            posts.insert(c, post);
            if (command.visibility() != null) {
                declareVisibility(c, post.id());
            }
            return detail(c, post, true);
        });
    }

    /** Changes the content of a post that is a draft or in review. An approved post is changed through a revision. */
    public PostDetail edit(PostActor actor, UUID id, Content content) throws SQLException {
        requireEditor(actor);
        return transactions.inTransaction(c -> {
            Post post = requirePost(c, id);
            Instant now = clock.instant();
            String playlist = null;
            if (content.channelId() != null && !content.channelId().equals(post.channelId())) {
                requireUsableChannel(c, content.channelId());
                playlist = playlistOf(c, post.seriesId(), content.channelId(), actor);
            }
            requireCover(c, content.coverAssetId());
            Post changed = post.edited(content, playlist, now);
            if (content.visibility() != null) {
                declareVisibility(c, id);
            }
            return detail(c, posts.update(c, changed), true);
        });
    }

    /**
     * Declares the made-for-kids answer of a YouTube post (change add-youtube-integration). The answer is required before
     * scheduling; no platform default is used. Allowed while the post can still be edited.
     */
    public PostDetail declareMadeForKids(PostActor actor, UUID id, boolean madeForKids) throws SQLException {
        return declareYouTube(actor, id, madeForKids, null);
    }

    /**
     * Declares the YouTube answers of a post: the made-for-kids answer and, when given, whether the post is planned as a Short
     * (a null {@code planShort} keeps the current answer). Allowed while the post can still be edited.
     */
    public PostDetail declareYouTube(PostActor actor, UUID id, boolean madeForKids, Boolean planShort) throws SQLException {
        requireEditor(actor);
        return transactions.inTransaction(c -> {
            Post post = requirePost(c, id);
            if (!post.status().editable()) {
                throw new ProblemException(409, "Conflict", "A post in status " + post.status().value()
                        + " is not edited; propose a revision instead.");
            }
            YouTubeDeclaration current = posts.declaration(c, id);
            posts.putDeclaration(c, id, new YouTubeDeclaration(current.visibilityDeclared(), madeForKids,
                    planShort == null ? current.plannedShort() : planShort));
            return detail(c, post, true);
        });
    }

    /** Records that the visibility of a post was chosen by a person, which the platform needs before scheduling. */
    private void declareVisibility(Connection c, UUID id) throws SQLException {
        YouTubeDeclaration current = posts.declaration(c, id);
        posts.putDeclaration(c, id, new YouTubeDeclaration(true, current.madeForKids(), current.plannedShort()));
    }

    /** Deletes a draft or a post in review. A post that was approved, scheduled or published is kept. */
    public void delete(PostActor actor, UUID id) throws SQLException {
        requireEditor(actor);
        transactions.inTransaction(c -> {
            Post post = requirePost(c, id);
            if (!post.status().editable()) {
                throw new ProblemException(409, "Conflict", "Only a draft or a post in review can be deleted.");
            }
            posts.delete(c, id);
            return null;
        });
    }

    public PostDetail submit(PostActor actor, UUID id) throws SQLException {
        requireEditor(actor);
        return transactions.inTransaction(c -> {
            Post changed = requirePost(c, id).submitted(clock.instant());
            return detail(c, posts.update(c, changed), true);
        });
    }

    // ---------------------------------------------------------------- approval

    /**
     * A publisher approves a post in review. Only the publisher role approves, and only from the web application: a token
     * or an MCP client may not approve, whatever role it holds.
     */
    public PostDetail approve(PostActor actor, UUID id) throws SQLException {
        requirePublisher(actor);
        if (!actor.interactive()) {
            throw new ProblemException(403, "Forbidden", "Approval is only possible in the web application.");
        }
        return transactions.inTransaction(c -> {
            Post changed = requirePost(c, id).approved(actor.personId(), clock.instant());
            return detail(c, posts.update(c, changed), true);
        });
    }

    /**
     * Schedules an approved post, or a failed post that a person wants to publish again. The dry run runs now and its
     * blocking problems refuse the schedule with 422; warnings are kept and shown on the calendar.
     */
    public PostDetail schedule(PostActor actor, UUID id) throws SQLException {
        requirePublisher(actor);
        Optional<Set<String>> owned = ownedPlaylistsOf(id);
        return transactions.inTransaction(c -> {
            Instant now = clock.instant();
            Post post = requirePost(c, id);
            if (post.status() != PostStatus.APPROVED && post.status() != PostStatus.FAILED) {
                throw new ProblemException(409, "Conflict", "Only an approved or failed post can be scheduled.");
            }
            if (post.scheduledAt() == null || !post.scheduledAt().isAfter(now)) {
                throw new ProblemException(422, "Unprocessable Entity", "The post needs a scheduled time in the future.");
            }
            Channel channel = requireChannel(c, post.channelId());
            List<DryRun.Problem> problems = PostFacts.scheduleProblems(c, posts, channels, post, channel, owned);
            if (DryRun.blocking(problems)) {
                throw new DryRunRefusal(problems);
            }
            Post scheduled = post.scheduled(now);
            return detail(c, posts.update(c, scheduled), true);
        });
    }

    // ---------------------------------------------------------------- planning

    /**
     * Plans one finished asset onto several channels: one draft per channel with its own text and time. A channel that
     * the plan may not use is refused and named; the other posts are stored.
     */
    public PlanResult planChannels(PostActor actor, PlanCommand command) throws SQLException {
        requireEditor(actor);
        return transactions.inTransaction(c -> {
            List<PostDetail> created = new ArrayList<>();
            List<Refusal> refused = new ArrayList<>();
            if (command.items() == null || command.items().isEmpty()) {
                throw new ProblemException(400, "Bad Request", "A plan needs at least one channel.");
            }
            for (PlanItem item : command.items()) {
                try {
                    channelForPlan(c, item.channelId());
                    Post post = draft(c, actor, item.channelId(), command.assetId(), command.coverAssetId(),
                            command.seriesId(), command.sourcePostId(), item.title(), item.description(), item.hashtags(),
                            item.visibility(), item.scheduledAt());
                    posts.insert(c, post);
                    if (item.visibility() != null) {
                        declareVisibility(c, post.id());
                    }
                    created.add(detail(c, post, true));
                } catch (ProblemException refusal) {
                    refused.add(new Refusal(item.channelId(), refusal.detail()));
                }
            }
            return new PlanResult(created, refused);
        });
    }

    /**
     * Plans the next episodes of a series onto a channel: each episode takes the next free slot of the series' weekday and
     * posting hour in the installation time zone. A slot that already holds a post on the channel is skipped and named.
     */
    public EpisodeResult planEpisodes(PostActor actor, UUID seriesId, EpisodeCommand command) throws SQLException {
        requireEditor(actor);
        if (command.count() < 1 || command.count() > EPISODES_MAX) {
            throw new ProblemException(400, "Bad Request", "Between 1 and " + EPISODES_MAX + " episodes can be planned at once.");
        }
        return transactions.inTransaction(c -> {
            Instant now = clock.instant();
            ContentSeries found = series.find(c, seriesId, actor.roles());
            if (!found.active()) {
                throw new ProblemException(409, "Conflict", "An inactive series cannot be planned.");
            }
            Channel channel = requireChannel(c, command.channelId());
            requireUsableChannel(c, command.channelId());
            requireCover(c, command.coverAssetId());
            Instant until = now.plusSeconds(7L * 24 * 3600 * (SEARCH_WEEKS + command.count()));
            Map<Instant, Post> taken = new HashMap<>();
            for (Post post : posts.scheduledOn(c, channel.id(), now, until)) {
                taken.put(post.scheduledAt(), post);
            }
            String playlist = playlistOf(found, channel.id());
            List<PostDetail> planned = new ArrayList<>();
            List<SkippedSlot> skipped = new ArrayList<>();
            LocalDate day = now.atZone(zone).toLocalDate();
            int days = 7 * (SEARCH_WEEKS + command.count());
            for (int offset = 0; offset <= days && planned.size() < command.count(); offset++) {
                LocalDate date = day.plusDays(offset);
                if (date.getDayOfWeek().getValue() != found.weekday()) {
                    continue;
                }
                Instant slot = date.atTime(found.postingSlot(), 0).atZone(zone).toInstant();
                if (!slot.isAfter(now)) {
                    continue;
                }
                Post holder = taken.get(slot);
                if (holder != null) {
                    skipped.add(new SkippedSlot(slot, holder.id(), "A post already holds this slot on this channel."));
                    continue;
                }
                Post post = Post.draft(identifiers.next(), actor.personId(), new PostDraft(channel.id(), command.assetId(),
                        command.coverAssetId(), seriesId, null, found.name(), found.openingFormula(),
                        List.of(found.hashtag()), null, playlist, slot), now);
                posts.insert(c, post);
                taken.put(slot, post);
                planned.add(detail(c, post, true));
            }
            return new EpisodeResult(planned, skipped);
        });
    }

    // --------------------------------------------------------------- revisions

    /** A proposed revision of a post, stored as its own record. The post itself does not change. */
    public PostRevision propose(PostActor actor, UUID id, Content content, String rationale) throws SQLException {
        requireEditor(actor);
        return transactions.inTransaction(c -> {
            Post post = requirePost(c, id);
            if (!post.status().revisable()) {
                throw new ProblemException(409, "Conflict", "A post in status " + post.status().value()
                        + " takes no revisions.");
            }
            if (post.handedOver()) {
                throw new ProblemException(409, "Conflict", "The post was already handed to its platform and cannot be revised.");
            }
            if (content.channelId() != null) {
                requireUsableChannel(c, content.channelId());
            }
            requireCover(c, content.coverAssetId());
            PostRevision revision = new PostRevision(identifiers.next(), id, actor.personId(), rationale, content,
                    PostRevision.Status.PENDING, null, null, clock.instant());
            posts.insertRevision(c, revision);
            return revision;
        });
    }

    public List<PostRevision> revisions(UUID id) throws SQLException {
        return transactions.inTransaction(c -> {
            requirePost(c, id);
            return posts.revisions(c, id);
        });
    }

    /**
     * A publisher accepts a pending revision in the web application. The content changes; an approved, scheduled or failed
     * post returns to review, so the approval happens again.
     */
    public PostDetail acceptRevision(PostActor actor, UUID id, UUID revisionId) throws SQLException {
        requirePublisher(actor);
        if (!actor.interactive()) {
            throw new ProblemException(403, "Forbidden", "A revision is accepted only in the web application.");
        }
        return transactions.inTransaction(c -> {
            Post post = requirePost(c, id);
            PostRevision revision = requirePending(c, id, revisionId);
            if (post.handedOver()) {
                throw new ProblemException(409, "Conflict", "The post was already handed to its platform and cannot be revised.");
            }
            Instant now = clock.instant();
            String playlist = null;
            if (revision.content().channelId() != null && !revision.content().channelId().equals(post.channelId())) {
                requireUsableChannel(c, revision.content().channelId());
                playlist = playlistOf(c, post.seriesId(), revision.content().channelId(), actor);
            }
            Post changed = posts.update(c, post.revised(revision.content(), playlist, now));
            posts.updateRevision(c, revision.decided(PostRevision.Status.ACCEPTED, actor.personId(), now));
            return detail(c, changed, true);
        });
    }

    public PostRevision rejectRevision(PostActor actor, UUID id, UUID revisionId) throws SQLException {
        requirePublisher(actor);
        return transactions.inTransaction(c -> {
            requirePost(c, id);
            PostRevision revision = requirePending(c, id, revisionId);
            PostRevision decided = revision.decided(PostRevision.Status.REJECTED, actor.personId(), clock.instant());
            posts.updateRevision(c, decided);
            return decided;
        });
    }

    // ---------------------------------------------------------------- calendar

    /** The posts of a window, planned and published together, with their problems computed on read. */
    public CalendarPage calendar(CalendarRange range, UUID channelId, UUID seriesId, PostStatus status) throws SQLException {
        return transactions.inTransaction(c -> {
            List<Post> rows = posts.window(c, range.from(), range.to(), channelId, seriesId, status, CALENDAR_LIMIT + 1);
            boolean truncated = rows.size() > CALENDAR_LIMIT;
            List<PostDetail> items = new ArrayList<>();
            for (Post post : truncated ? rows.subList(0, CALENDAR_LIMIT) : rows) {
                Optional<Channel> channel = channels.findChannel(c, post.channelId());
                if (channel.isPresent()) {
                    items.add(detail(c, post, false));
                }
            }
            return new CalendarPage(items, truncated);
        });
    }

    /**
     * The events of a person's calendar feed: planned posts in the next {@link #FEED_DAYS} days and, for a publisher, the
     * posts in review that wait for approval. Stable identifiers and the revision as sequence let a client replace an event.
     */
    public List<IcsFeed.Event> feedEvents(Instant now, boolean publisher) throws SQLException {
        return transactions.inTransaction(c -> {
            List<IcsFeed.Event> events = new ArrayList<>();
            List<Post> rows = posts.window(c, now, now.plusSeconds(86_400L * FEED_DAYS), null, null, null, CALENDAR_LIMIT);
            for (Post post : rows) {
                if (post.scheduledAt() == null || post.status() == PostStatus.PUBLISHED
                        || post.status() == PostStatus.FAILED) {
                    continue;
                }
                Optional<Channel> channel = channels.findChannel(c, post.channelId());
                String channelName = channel.map(Channel::displayName).orElse("");
                events.add(new IcsFeed.Event("post-" + post.id() + "@menura", "Planned: " + post.title(),
                        "Channel: " + channelName + ". Status: " + post.status().value(), post.scheduledAt(),
                        post.revision(), post.updatedAt()));
                if (publisher && post.status() == PostStatus.IN_REVIEW) {
                    events.add(new IcsFeed.Event("approval-" + post.id() + "@menura", "Approval due: " + post.title(),
                            "Channel: " + channelName + ". Approve in the web application.", post.scheduledAt(),
                            post.revision(), post.updatedAt()));
                }
            }
            return events;
        });
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A new draft with the series defaults filled into every field not given. The post copies the text, so later series
     * changes never reach it.
     */
    private Post draft(Connection c, PostActor actor, UUID channelId, UUID assetId, UUID coverAssetId, UUID seriesId,
                       UUID sourcePostId, String title, String description, List<String> hashtags, Visibility visibility,
                       Instant scheduledAt) throws SQLException {
        Channel channel = requireChannel(c, channelId);
        requireFinished(c, assetId);
        requireCover(c, coverAssetId);
        ContentSeries owner = seriesId == null ? null : series.find(c, seriesId, actor.roles());
        if (sourcePostId != null) {
            Post source = requirePost(c, sourcePostId);
            if (!source.assetId().equals(assetId)) {
                throw new ProblemException(422, "Unprocessable Entity", "A recycled post reuses the asset of its source.");
            }
        }
        String resolvedTitle = title != null ? title : owner == null ? null : owner.name();
        String resolvedDescription = description != null ? description : owner == null ? null : owner.openingFormula();
        List<String> resolvedHashtags = hashtags != null && !hashtags.isEmpty() ? hashtags
                : owner == null ? List.of() : List.of(owner.hashtag());
        String playlist = owner == null ? null : playlistOf(owner, channel.id());
        if (resolvedTitle == null) {
            throw new ProblemException(400, "Bad Request", "The title is required.");
        }
        return Post.draft(identifiers.next(), actor.personId(), new PostDraft(channelId, assetId, coverAssetId, seriesId,
                sourcePostId, resolvedTitle, resolvedDescription, resolvedHashtags, visibility, playlist, scheduledAt),
                clock.instant());
    }

    /**
     * The playlists the channel of a post owns, read from the platform before the schedule transaction, so that no platform
     * call runs inside a database transaction. Empty when the post has no playlist, the channel cannot be used, or the
     * platform cannot list them; the dry run then reports the playlist as unverified.
     */
    private Optional<Set<String>> ownedPlaylistsOf(UUID postId) throws SQLException {
        Optional<Post> post = transactions.inTransaction(c -> posts.find(c, postId));
        if (post.isEmpty() || post.get().playlistId() == null) {
            return Optional.empty();
        }
        Optional<Channel> channel = transactions.inTransaction(c -> channels.findChannel(c, post.get().channelId()));
        if (channel.isEmpty() || channel.get().state() != ConnectionState.CONNECTED) {
            return Optional.empty();
        }
        Optional<PlatformAdapter> adapter = platforms.find(channel.get().platform());
        if (adapter.isEmpty()) {
            return Optional.empty();
        }
        try {
            String token = connections.freshGrant(channel.get().id()).accessToken();
            return Optional.of(adapter.get().ownedPlaylistIds(token));
        } catch (ProblemException | PlatformException | PlatformAdapter.Unsupported unreadable) {
            return Optional.empty();
        }
    }

    private String playlistOf(Connection c, UUID seriesId, UUID channelId, PostActor actor) throws SQLException {
        if (seriesId == null) {
            return null;
        }
        return playlistOf(series.find(c, seriesId, actor.roles()), channelId);
    }

    private static String playlistOf(ContentSeries owner, UUID channelId) {
        var playlist = owner.playlists().get(channelId);
        return playlist == null ? null : playlist.playlistId();
    }

    private Post requirePost(Connection c, UUID id) throws SQLException {
        return posts.find(c, id).orElseThrow(() -> new ProblemException(404, "Not Found", "The post does not exist."));
    }

    private PostRevision requirePending(Connection c, UUID postId, UUID revisionId) throws SQLException {
        PostRevision revision = posts.findRevision(c, postId, revisionId)
                .orElseThrow(() -> new ProblemException(404, "Not Found", "The revision does not exist."));
        if (!revision.pending()) {
            throw new ProblemException(409, "Conflict", "The revision was already decided.");
        }
        return revision;
    }

    private Channel requireChannel(Connection c, UUID channelId) throws SQLException {
        return channels.findChannel(c, channelId).orElseThrow(() -> new ProblemException(422, "Unprocessable Entity",
                "The channel does not exist."));
    }

    private void requireUsableChannel(Connection c, UUID channelId) throws SQLException {
        Channel channel = requireChannel(c, channelId);
        if (channel.state() != ConnectionState.CONNECTED) {
            throw new ProblemException(422, "Unprocessable Entity", "The channel needs reconnection before it takes posts.");
        }
    }

    /** A channel a plan may use: it exists, is connected, and its platform is supported in this build. */
    private Channel channelForPlan(Connection c, UUID channelId) throws SQLException {
        Channel channel = channels.findChannel(c, channelId)
                .orElseThrow(() -> new ProblemException(422, "Unprocessable Entity", "The channel does not exist."));
        if (channel.state() != ConnectionState.CONNECTED) {
            throw new ProblemException(422, "Unprocessable Entity", "The channel needs reconnection before it takes posts.");
        }
        if (platforms.find(channel.platform()).isEmpty()) {
            throw new ProblemException(422, "Unprocessable Entity", "The platform of this channel is not supported in this build.");
        }
        return channel;
    }

    private void requireFinished(Connection c, UUID assetId) throws SQLException {
        if (posts.finishedAssetKind(c, assetId).isEmpty()) {
            throw new ProblemException(422, "Unprocessable Entity",
                    "Only finished assets recorded by the upload can be planned; this asset is not a finished asset.");
        }
    }

    private void requireCover(Connection c, UUID coverAssetId) throws SQLException {
        if (coverAssetId != null && !"image".equals(posts.finishedAssetKind(c, coverAssetId).orElse(null))) {
            throw new ProblemException(422, "Unprocessable Entity", "The cover must be a finished image.");
        }
    }

    private PostDetail detail(Connection c, Post post, boolean withAttempts) throws SQLException {
        Channel channel = requireChannel(c, post.channelId());
        String kind = posts.finishedAssetKind(c, post.assetId()).orElse(null);
        List<DryRun.Problem> problems = post.status() == PostStatus.PUBLISHED || post.status() == PostStatus.FAILED
                ? List.of() : PostFacts.problems(c, posts, channels, post, channel);
        return new PostDetail(post, channel, kind, posts.steps(c, post.id()),
                withAttempts ? posts.attempts(c, post.id()) : List.of(), problems, posts.declaration(c, post.id()),
                posts.upload(c, post.id()).orElse(null));
    }

    private static void requireEditor(PostActor actor) {
        if (!actor.has(Role.EDITOR) && !actor.has(Role.PUBLISHER)) {
            throw new ProblemException(403, "Forbidden", "Your roles do not allow this action.");
        }
    }

    private static void requirePublisher(PostActor actor) {
        if (!actor.has(Role.PUBLISHER)) {
            throw new ProblemException(403, "Forbidden", "Only a publisher may do this.");
        }
    }
}
