package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublicationStatus;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.StepwisePublisher;
import net.onelitefeather.menura.core.channel.UploadSessionExpired;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.library.ChapterLines;
import net.onelitefeather.menura.core.library.ChapterMark;
import net.onelitefeather.menura.core.library.TimelineChapters;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostListener;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.TokenCipher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One run of the publication job (change add-content-distribution): claims the due scheduled posts, publishes them or hands
 * them to the platform's own timed publication, confirms handed-over posts and records every step and attempt.
 *
 * <p>Idempotency: a post is claimed by one run at a time (a row lease); an attempt is recorded before the platform is
 * called; the platform is called again only after a failure that proves nothing was published (a refused or rate-limited
 * request, or a failed token refresh). An ambiguous outcome, such as a platform that became unreachable during the
 * publish call, ends the post as failed with an unknown outcome, and a person decides what happens next. A post whose
 * claim expired without a recorded outcome is handled the same way.
 *
 * <p>A platform that publishes in steps (change add-youtube-integration) runs upload, schedule (read-back), playlist and
 * thumbnail as separate steps. Each step is recorded when it finishes, a failed step is retried on its own, and a retry
 * never repeats a finished step: an upload resumes from the byte the platform confirmed, after its status is read. Every
 * platform call of such a step is charged to the channel's quota before it is made, and refused when the quota would be
 * exceeded.
 *
 * <p>The platform's published item is recorded as an external post, the same record an import writes, so the imported and
 * the published content share one record of what is live on each channel.
 */
public final class PublicationRunner {

    static final String RECONNECT = "The channel needs reconnection before this post can go out.";
    static final String REFUSED = "The platform refused the publication. The channel may need reconnection.";
    static final String UNREACHABLE = "The platform could not be reached.";
    static final String RATE_LIMITED = "The platform is rate limiting requests.";
    static final String UNKNOWN = "The platform outcome is unknown. The post was not retried; check the platform before scheduling it again.";
    static final String NOT_SUPPORTED = "Publishing to this platform is not available in this build.";
    static final String NOT_PUBLISHED = "The platform did not publish the item.";
    static final String BLOCKED = "The schedule dry run blocks publication: ";
    static final String STEPWISE_UNAVAILABLE = "The step-wise publication of this platform is not configured in this job.";
    static final String QUOTA_DELAYED = "The daily quota of the channel is used up; the publication is delayed until it resets.";
    static final String SCHEDULE_MISMATCH = "The platform did not keep the scheduled time of the video.";
    static final String SESSION_RESTART = "The upload session expired; the upload starts again on the next attempt.";
    static final String UNCONFIRMED = "The platform did not confirm the bytes of the upload.";
    static final String NO_ASSET = "The post has no finished asset.";
    static final String UNSUPPORTED_FORMAT = "The format of the finished asset cannot be uploaded to this platform.";
    static final String THUMBNAIL_FORMAT = "The thumbnail must be a JPEG or PNG image.";
    static final String THUMBNAIL_FAILED = "The platform could not process the video, so no thumbnail was set.";
    static final String WAITING_PROCESSING = "The platform is still processing the video; the thumbnail waits for it.";
    static final String DESCRIPTION_TOO_LONG = "The description with its chapter lines is longer than "
            + PublishRules.YOUTUBE.maxDescription() + " characters.";

    /** The bytes sent per chunk of a resumable upload: a multiple of 256 KiB, as the platform requires for all but the last. */
    static final int CHUNK_BYTES = 8 * 1024 * 1024;
    /** The largest thumbnail the platform accepts (checked 2026-11-08 at https://developers.google.com/youtube/v3/docs/thumbnails/set). */
    static final long THUMBNAIL_MAX_BYTES = 50L * 1024 * 1024;

    /** What one run did. Counts only; no title, token or URL. */
    public record Report(int claimed, int published, int handedOver, int confirmed, int retried, int failed, int waiting) {
    }

    /**
     * What a step-wise platform needs besides its port: the token key that seals the session location of an upload, the
     * stored media of the finished assets, and the quota ledger that charges every platform call.
     */
    public record Stepwise(TokenCipher cipher, MediaStorage media, QuotaLedger quota, TimelineChapters chapters) {
    }

    private enum Action { PUBLISH, HANDOVER, CONFIRM, FINISH }

    private record Claim(Post post, Action action, Channel channel) {
    }

    /** The result of one platform call. */
    private record Result(boolean done, String platformItemId, String url, String reason, boolean retryable,
                          boolean unknown, boolean waiting) {

        static Result published(String id, String url) {
            return new Result(true, id, url, null, false, false, false);
        }

        static Result failure(String reason, boolean retryable, boolean unknown) {
            return new Result(false, null, null, reason, retryable, unknown, false);
        }

        static Result still() {
            return new Result(false, null, null, null, false, false, true);
        }
    }

    /** A step that did not finish: its step, why, whether a retry may help, and until when a deferral lasts (quota). */
    private record Stop(PostStep step, String reason, boolean retryable, Instant deferUntil) {
    }

    /** Stops the step sequence of one attempt; it never leaves the runner. */
    private static final class StepStop extends RuntimeException {
        private final Stop stop;

        StepStop(Stop stop) {
            super(stop.reason(), null, false, false);
            this.stop = stop;
        }
    }

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final PostStore posts;
    private final ChannelStore channels;
    private final ConnectionService connections;
    private final PlatformRegistry platforms;
    private final ExternalPostStore externalPosts;
    private final ExternalPostListener listener;
    private final PublishSettings settings;
    private final Stepwise stepwise;

    public PublicationRunner(Transactions transactions, Clock clock, IdentifierGenerator identifiers, PostStore posts,
                             ChannelStore channels, ConnectionService connections, PlatformRegistry platforms,
                             ExternalPostStore externalPosts, ExternalPostListener listener, PublishSettings settings) {
        this(transactions, clock, identifiers, posts, channels, connections, platforms, externalPosts, listener, settings,
                null);
    }

    /** A runner with the step-wise publication configured; null leaves step-wise platforms unpublishable. */
    public PublicationRunner(Transactions transactions, Clock clock, IdentifierGenerator identifiers, PostStore posts,
                             ChannelStore channels, ConnectionService connections, PlatformRegistry platforms,
                             ExternalPostStore externalPosts, ExternalPostListener listener, PublishSettings settings,
                             Stepwise stepwise) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.posts = posts;
        this.channels = channels;
        this.connections = connections;
        this.platforms = platforms;
        this.externalPosts = externalPosts;
        this.listener = listener;
        this.settings = settings;
        this.stepwise = stepwise;
    }

    public Report runOnce() throws SQLException {
        Instant now = clock.instant();
        String owner = "publish-" + identifiers.next();
        List<Claim> claims = transactions.inTransaction(c -> claim(c, now, now.plus(settings.handoverLead()), owner));
        int published = 0;
        int handedOver = 0;
        int confirmed = 0;
        int retried = 0;
        int failed = 0;
        int waiting = 0;
        for (Claim claim : claims) {
            switch (claim.action()) {
                case PUBLISH, HANDOVER, FINISH -> {
                    Outcome outcome = attempt(claim, now);
                    switch (outcome) {
                        case PUBLISHED -> published++;
                        case HANDED_OVER -> handedOver++;
                        case RETRY -> retried++;
                        case FAILED -> failed++;
                        case WAITING -> waiting++;
                        default -> {
                        }
                    }
                }
                case CONFIRM -> {
                    Outcome outcome = confirm(claim, now);
                    if (outcome == Outcome.PUBLISHED) {
                        confirmed++;
                    } else if (outcome == Outcome.FAILED) {
                        failed++;
                    } else {
                        waiting++;
                    }
                }
            }
        }
        return new Report(claims.size(), published, handedOver, confirmed, retried, failed, waiting);
    }

    private enum Outcome { PUBLISHED, HANDED_OVER, RETRY, FAILED, WAITING }

    // ------------------------------------------------------------------ claim

    private List<Claim> claim(Connection c, Instant now, Instant horizon, String owner) throws SQLException {
        List<Claim> out = new ArrayList<>();
        for (Post candidate : posts.claimCandidates(c, now, horizon, settings.batchSize())) {
            Post post = candidate;
            if (post.leaseOwner() != null) {
                if (post.externalId() == null && !resumable(c, post)) {
                    endUnknown(c, post, now);
                    continue;
                }
                post = post.released(now);
            }
            Post due = post;
            Optional<Channel> channel = channels.findChannel(c, due.channelId());
            Optional<Action> action = channel.isPresent() ? actionFor(c, due, channel.get(), now) : Optional.empty();
            if (action.isEmpty()) {
                if (due.leaseOwner() == null && candidate.leaseOwner() != null) {
                    posts.update(c, due);
                }
                continue;
            }
            Post leased = due.claimed(owner, now.plus(settings.lease()), now);
            posts.update(c, leased);
            out.add(new Claim(leased, action.get(), channel.orElseThrow()));
        }
        return out;
    }

    /** A claimed post whose run stopped mid-upload on a step-wise platform: the recorded session lets the next run resume. */
    private boolean resumable(Connection c, Post post) throws SQLException {
        return stepwise != null && posts.upload(c, post.id()).map(upload -> upload.session() != null).orElse(false);
    }

    private Optional<Action> actionFor(Connection c, Post post, Channel channel, Instant now) throws SQLException {
        Optional<PlatformAdapter> adapter = platforms.find(channel.platform());
        if (adapter.isEmpty()) {
            return Optional.empty();
        }
        if (adapter.get().stepwise().isPresent()) {
            return stepwiseAction(c, post, now);
        }
        if (adapter.get().capabilities().nativeScheduling()) {
            if (post.externalId() == null) {
                return post.scheduledAt().minus(settings.handoverLead()).isAfter(now) ? Optional.empty()
                        : Optional.of(Action.HANDOVER);
            }
            return post.scheduledAt().isAfter(now) ? Optional.empty() : Optional.of(Action.CONFIRM);
        }
        return post.scheduledAt().isAfter(now) ? Optional.empty() : Optional.of(Action.PUBLISH);
    }

    /**
     * The action of a step-wise post. An unuploaded post is published when due, or handed over to the platform's timed
     * publication within the lead time. An uploaded post with open steps is finished; otherwise it is confirmed at its time.
     */
    private Optional<Action> stepwiseAction(Connection c, Post post, Instant now) throws SQLException {
        if (post.externalId() == null) {
            if (!post.scheduledAt().isAfter(now)) {
                return Optional.of(Action.PUBLISH);
            }
            return post.scheduledAt().minus(settings.handoverLead()).isAfter(now) ? Optional.empty()
                    : Optional.of(Action.HANDOVER);
        }
        Set<PostStep> open = openSteps(c, post.id());
        boolean unfinished = open.contains(PostStep.SCHEDULE) || open.contains(PostStep.PLAYLIST)
                || open.contains(PostStep.THUMBNAIL);
        if (post.scheduledAt().isAfter(now)) {
            return unfinished ? Optional.of(Action.FINISH) : Optional.empty();
        }
        return unfinished ? Optional.of(Action.FINISH) : Optional.of(Action.CONFIRM);
    }

    /** The steps of a post that are not done. A thumbnail that failed for good does not stay open. */
    private Set<PostStep> openSteps(Connection c, java.util.UUID postId) throws SQLException {
        Map<PostStep, StepState> states = new EnumMap<>(PostStep.class);
        for (StepRecord step : posts.steps(c, postId)) {
            states.put(step.step(), step.state());
        }
        Set<PostStep> open = EnumSet.noneOf(PostStep.class);
        for (PostStep step : List.of(PostStep.UPLOAD, PostStep.SCHEDULE, PostStep.PLAYLIST, PostStep.THUMBNAIL)) {
            StepState state = states.get(step);
            if (state == StepState.DONE || (step == PostStep.THUMBNAIL && state == StepState.FAILED)) {
                continue;
            }
            open.add(step);
        }
        return open;
    }

    /** A claimed post whose run stopped before the platform answered: the outcome is unknown, and nothing is retried. */
    private void endUnknown(Connection c, Post post, Instant now) throws SQLException {
        for (AttemptRecord attempt : posts.attempts(c, post.id())) {
            if (attempt.finishedAt() == null) {
                posts.putAttempt(c, new AttemptRecord(post.id(), attempt.attempt(), attempt.startedAt(), now,
                        AttemptRecord.UNKNOWN, UNKNOWN));
            }
        }
        failSteps(c, post.id(), UNKNOWN, now);
        posts.update(c, post.failed(UNKNOWN, now));
    }

    // ---------------------------------------------------------------- publish

    private Outcome attempt(Claim claim, Instant now) throws SQLException {
        PlatformAdapter adapter = platforms.require(claim.channel().platform());
        boolean steps = adapter.stepwise().isPresent();
        if (steps && claim.action() == Action.FINISH && onlyThumbnailWaits(claim, now)) {
            return Outcome.WAITING;
        }
        Post started = transactions.inTransaction(c -> {
            Post current = posts.find(c, claim.post().id()).orElseThrow();
            int number = current.attempts() + 1;
            Post next = posts.update(c, current.attemptStarted(now));
            posts.putAttempt(c, new AttemptRecord(next.id(), number, now, null, null, null));
            boolean handover = claim.action() == Action.HANDOVER;
            if (!steps) {
                writeStep(c, next.id(), PostStep.UPLOAD, StepState.RUNNING, now, null);
                writeStep(c, next.id(), PostStep.METADATA, handover ? StepState.RUNNING : StepState.PENDING, now, null);
                writeStep(c, next.id(), PostStep.SCHEDULE, handover ? StepState.RUNNING : StepState.PENDING, now, null);
                writeStep(c, next.id(), PostStep.PUBLISH, StepState.PENDING, now, null);
            }
            return next;
        });

        Result result = transactions.inTransaction(c -> {
            Channel fresh = channels.findChannel(c, started.channelId()).orElseThrow();
            List<DryRun.Problem> problems = PostFacts.problems(c, posts, channels, started, fresh);
            return DryRun.blocking(problems)
                    ? Result.failure(BLOCKED + DryRun.blockingCodes(problems).stream().sorted().collect(Collectors.joining(", ")),
                    false, false)
                    : null;
        });
        if (result != null) {
            return finish(started, claim, result, now);
        }

        String token;
        try {
            token = connections.freshGrant(started.channelId()).accessToken();
        } catch (ProblemException refusal) {
            return finish(started, claim, Result.failure(RECONNECT, false, false), now);
        } catch (PlatformException failure) {
            return finish(started, claim, Result.failure(reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                    false), now);
        }
        if (steps) {
            return stepwise(started, claim, adapter.stepwise().orElseThrow(), token, now);
        }
        boolean handover = claim.action() == Action.HANDOVER;
        PlatformAdapter.PublishRequest request = new PlatformAdapter.PublishRequest(started.title(), started.visibility(),
                handover ? started.scheduledAt() : null);
        try {
            PlatformAdapter.PublishResult published = adapter.publish(token, request);
            return finish(started, claim, Result.published(published.platformItemId(), published.url()), now);
        } catch (PlatformException failure) {
            return switch (failure.kind()) {
                case RATE_LIMITED -> finish(started, claim, Result.failure(RATE_LIMITED, true, false), now);
                case REJECTED -> finish(started, claim, Result.failure(REFUSED, false, false), now);
                case UNAVAILABLE -> finish(started, claim, Result.failure(UNKNOWN, false, true), now);
            };
        } catch (PlatformAdapter.Unsupported unsupported) {
            return finish(started, claim, Result.failure(NOT_SUPPORTED, false, false), now);
        } catch (RuntimeException unexpected) {
            return finish(started, claim, Result.failure(UNKNOWN, false, true), now);
        }
    }

    /** Writes the outcome of one attempt: published, handed over, a retry, or a final failure. */
    private Outcome finish(Post started, Claim claim, Result result, Instant now) throws SQLException {
        return transactions.inTransaction(c -> {
            Post current = posts.find(c, started.id()).orElseThrow();
            if (result.done()) {
                if (claim.action() == Action.HANDOVER) {
                    Post handed = posts.update(c, current.handedOver(result.platformItemId(), result.url(), now).released(now));
                    writeStep(c, handed.id(), PostStep.UPLOAD, StepState.DONE, now, null);
                    writeStep(c, handed.id(), PostStep.METADATA, StepState.DONE, now, null);
                    writeStep(c, handed.id(), PostStep.SCHEDULE, StepState.DONE, now, null);
                    writeStep(c, handed.id(), PostStep.PUBLISH, StepState.PENDING, now, null);
                    endAttempt(c, handed.id(), AttemptRecord.HANDED_OVER, null, now);
                    return Outcome.HANDED_OVER;
                }
                markPublished(c, current, result.platformItemId(), result.url(), now);
                endAttempt(c, current.id(), AttemptRecord.PUBLISHED, null, now);
                return Outcome.PUBLISHED;
            }
            if (result.waiting()) {
                posts.update(c, current.released(now));
                return Outcome.WAITING;
            }
            if (result.retryable() && settings.retry().mayRetry(current.attempts())) {
                Instant next = settings.retry().nextAttempt(current.attempts(), now);
                posts.update(c, current.retryAt(result.reason(), next, now).released(now));
                failSteps(c, current.id(), result.reason(), now);
                endAttempt(c, current.id(), AttemptRecord.RETRY_SCHEDULED, result.reason(), now);
                return Outcome.RETRY;
            }
            posts.update(c, current.failed(result.reason(), now));
            failSteps(c, current.id(), result.reason(), now);
            endAttempt(c, current.id(), result.unknown() ? AttemptRecord.UNKNOWN : AttemptRecord.FAILED, result.reason(), now);
            return Outcome.FAILED;
        });
    }

    // ---------------------------------------------------------- step-wise publication

    /**
     * Runs the steps of a step-wise post that are not done, each on its own, and records the outcome. The upload comes
     * first; a post handed over to the platform's timed publication stays scheduled until its time, and a post published
     * at once is marked published when its steps are done. A waiting thumbnail (the video is still processing) keeps the
     * post open without a failure.
     */
    private Outcome stepwise(Post started, Claim claim, StepwisePublisher platform, String token, Instant now)
            throws SQLException {
        if (stepwise == null) {
            return finish(started, claim, Result.failure(STEPWISE_UNAVAILABLE, false, false), now);
        }
        Set<PostStep> done = transactions.inTransaction(c -> doneSteps(c, started.id()));
        Post post = started;
        boolean waiting = false;
        try {
            if (post.externalId() == null) {
                post = upload(post, platform, token, now, claim.channel().platform());
            }
            if (!done.contains(PostStep.SCHEDULE)) {
                readBack(post, platform, token, now);
            }
            if (!done.contains(PostStep.PLAYLIST)) {
                playlist(post, platform, token, now);
            }
            if (!done.contains(PostStep.THUMBNAIL)) {
                waiting = !thumbnail(post, platform, token, now);
            }
        } catch (StepStop stop) {
            return stepFailed(started, stop.stop, now);
        }
        boolean timed = post.scheduledAt().isAfter(now);
        if (timed) {
            return transactions.inTransaction(c -> {
                Post current = posts.find(c, started.id()).orElseThrow();
                posts.update(c, current.handedOver(current.externalId(), current.externalUrl(), now).released(now));
                endAttempt(c, current.id(), AttemptRecord.HANDED_OVER, null, now);
                return Outcome.HANDED_OVER;
            });
        }
        if (waiting) {
            return transactions.inTransaction(c -> {
                Post current = posts.find(c, started.id()).orElseThrow();
                revokeAttempt(c, current);
                posts.update(c, current.attemptRevoked(now).released(now));
                return Outcome.WAITING;
            });
        }
        return transactions.inTransaction(c -> {
            Post current = posts.find(c, started.id()).orElseThrow();
            markPublished(c, current, current.externalId(), current.externalUrl(), now);
            endAttempt(c, current.id(), AttemptRecord.PUBLISHED, null, now);
            return Outcome.PUBLISHED;
        });
    }

    /** Writes the stop of a step: a deferral by quota, a retry of that step, or a final failure of the post. */
    private Outcome stepFailed(Post started, Stop stop, Instant now) throws SQLException {
        return transactions.inTransaction(c -> {
            Post current = posts.find(c, started.id()).orElseThrow();
            if (stop.deferUntil() != null) {
                revokeAttempt(c, current);
                posts.update(c, current.attemptRevoked(now).retryAt(stop.reason(), stop.deferUntil(), now).released(now));
                return Outcome.RETRY;
            }
            writeStep(c, current.id(), stop.step(), StepState.FAILED, now, stop.reason());
            if (stop.retryable() && settings.retry().mayRetry(current.attempts())) {
                Instant next = settings.retry().nextAttempt(current.attempts(), now);
                posts.update(c, current.retryAt(stop.reason(), next, now).released(now));
                endAttempt(c, current.id(), AttemptRecord.RETRY_SCHEDULED, stop.reason(), now);
                return Outcome.RETRY;
            }
            posts.update(c, current.failed(stop.reason(), now));
            endAttempt(c, current.id(), AttemptRecord.FAILED, stop.reason(), now);
            return Outcome.FAILED;
        });
    }

    /**
     * The description the platform receives: the post text, then for YouTube one chapter line per mark of the timeline the
     * finished asset was made from. A description the platform would refuse stops the step before the upload starts.
     */
    private String descriptionOf(Post post, String platformName) throws SQLException {
        List<ChapterMark> chapters = YouTubeAdapter.PLATFORM.equals(platformName)
                ? transactions.inTransaction(c -> stepwise.chapters().chaptersOf(c, post.assetId()))
                : List.of();
        String description = ChapterLines.appendTo(post.description(), chapters);
        if (description.length() > PublishRules.YOUTUBE.maxDescription()) {
            throw new StepStop(new Stop(PostStep.UPLOAD, DESCRIPTION_TOO_LONG, false, null));
        }
        return description;
    }

    /**
     * The upload: starts a session only when none is recorded, reads the confirmed bytes before any resumption, sends the
     * asset chunk by chunk from the stored object, and records the platform item once the last byte is confirmed.
     */
    private Post upload(Post post, StepwisePublisher platform, String token, Instant now, String platformName)
            throws SQLException {
        FinishedAsset asset = transactions.inTransaction(c -> posts.finishedAsset(c, post.assetId()))
                .orElseThrow(() -> new StepStop(new Stop(PostStep.UPLOAD, NO_ASSET, false, null)));
        String contentType = contentTypeOf(asset.format());
        if (contentType == null) {
            throw new StepStop(new Stop(PostStep.UPLOAD, UNSUPPORTED_FORMAT, false, null));
        }
        long total = asset.sizeBytes();
        PostUpload existing = transactions.inTransaction(c -> posts.upload(c, post.id())).orElse(null);
        String session = existing == null || existing.session() == null ? null : openSession(post.id(), existing.session());
        long confirmed = existing == null ? 0 : existing.confirmedBytes();
        if (session != null) {
            try {
                StepwisePublisher.UploadProgress status = platform.uploadStatus(token, session, total);
                if (status.complete()) {
                    return complete(post, status, total, now);
                }
                confirmed = status.confirmedBytes();
                saveUpload(post.id(), new PostUpload(sealSession(post.id(), session), total, confirmed, null, null), now);
            } catch (UploadSessionExpired expired) {
                session = null;
                confirmed = 0;
                saveUpload(post.id(), new PostUpload(null, total, 0, null, null), now);
            } catch (PlatformException failure) {
                throw new StepStop(new Stop(PostStep.UPLOAD, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                        null));
            }
        }
        if (session == null) {
            String description = descriptionOf(post, platformName);
            charge(post, QuotaBudget.VIDEOS_INSERT, now, PostStep.UPLOAD);
            YouTubeDeclaration declaration = transactions.inTransaction(c -> posts.declaration(c, post.id()));
            boolean timed = post.scheduledAt().isAfter(now);
            try {
                session = platform.startUpload(token, new StepwisePublisher.UploadStart(post.title(), description,
                        tagsOf(post), timed ? PlatformAdapter.Visibility.PRIVATE : post.visibility(),
                        declaration.madeForKids() == null ? Boolean.FALSE : declaration.madeForKids(),
                        timed ? post.scheduledAt().truncatedTo(ChronoUnit.SECONDS) : null, total, contentType));
            } catch (PlatformException failure) {
                throw new StepStop(new Stop(PostStep.UPLOAD, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                        null));
            }
            saveUpload(post.id(), new PostUpload(sealSession(post.id(), session), total, 0, null, null), now);
        }
        while (confirmed < total) {
            int length = (int) Math.min(CHUNK_BYTES, total - confirmed);
            byte[] chunk;
            try {
                chunk = stepwise.media().read(asset.storageKey(), confirmed, length);
            } catch (IOException unreadable) {
                throw new StepStop(new Stop(PostStep.UPLOAD, "The stored video could not be read.", true, null));
            }
            if (chunk.length != length) {
                throw new StepStop(new Stop(PostStep.UPLOAD, "The stored video is shorter than its recorded size.", false, null));
            }
            StepwisePublisher.UploadProgress progress;
            try {
                progress = platform.sendChunk(token, session, confirmed, total, contentType, chunk);
            } catch (UploadSessionExpired expired) {
                saveUpload(post.id(), new PostUpload(null, total, 0, null, null), now);
                throw new StepStop(new Stop(PostStep.UPLOAD, SESSION_RESTART, true, null));
            } catch (PlatformException failure) {
                throw new StepStop(new Stop(PostStep.UPLOAD, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                        null));
            }
            if (progress.complete()) {
                return complete(post, progress, total, now);
            }
            if (progress.confirmedBytes() <= confirmed) {
                throw new StepStop(new Stop(PostStep.UPLOAD, UNCONFIRMED, true, null));
            }
            confirmed = progress.confirmedBytes();
            saveUpload(post.id(), new PostUpload(sealSession(post.id(), session), total, confirmed, null, null), now);
        }
        StepwisePublisher.UploadProgress status = platform.uploadStatus(token, session, total);
        if (status.complete()) {
            return complete(post, status, total, now);
        }
        throw new StepStop(new Stop(PostStep.UPLOAD, UNCONFIRMED, true, null));
    }

    /** Records the finished upload: the platform item is kept on the post, and the session is discarded. */
    private Post complete(Post post, StepwisePublisher.UploadProgress progress, long total, Instant now) throws SQLException {
        return transactions.inTransaction(c -> {
            Post current = posts.find(c, post.id()).orElseThrow();
            Post uploaded = posts.update(c, current.uploaded(progress.platformItemId(), progress.url(), now));
            writeStep(c, uploaded.id(), PostStep.UPLOAD, StepState.DONE, now, null);
            writeStep(c, uploaded.id(), PostStep.METADATA, StepState.DONE, now, null);
            posts.putUpload(c, uploaded.id(), new PostUpload(null, total, total, null, null), now);
            return uploaded;
        });
    }

    /**
     * The read-back of the applied state: a timed video must be private with the scheduled time, and the applied visibility
     * is compared with the requested one; a mismatch is kept on the post as a problem, not dropped.
     */
    private void readBack(Post post, StepwisePublisher platform, String token, Instant now) throws SQLException {
        charge(post, QuotaBudget.VIDEOS_LIST, now, PostStep.SCHEDULE);
        StepwisePublisher.VideoState state;
        try {
            state = platform.video(token, post.externalId());
        } catch (PlatformException failure) {
            throw new StepStop(new Stop(PostStep.SCHEDULE, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                    null));
        }
        boolean timed = post.scheduledAt().isAfter(now);
        if (timed && !post.scheduledAt().truncatedTo(ChronoUnit.SECONDS).equals(state.publishAt())) {
            throw new StepStop(new Stop(PostStep.SCHEDULE, SCHEDULE_MISMATCH, false, null));
        }
        PlatformAdapter.Visibility expected = timed ? PlatformAdapter.Visibility.PRIVATE : post.visibility();
        String applied = state.visibility() == null ? null : privacyOf(state.visibility());
        String problem = state.visibility() == expected ? null
                : "The platform applies " + (applied == null ? "an unknown visibility" : applied) + " instead of "
                + privacyOf(expected) + ".";
        transactions.inTransaction(c -> {
            PostUpload upload = posts.upload(c, post.id()).orElse(new PostUpload(null, 0, 0, null, null));
            posts.putUpload(c, post.id(), upload.applied(applied, problem), now);
            writeStep(c, post.id(), PostStep.SCHEDULE, StepState.DONE, now, null);
            return null;
        });
    }

    /** The playlist insert of the post's playlist, or nothing when the post has none. */
    private void playlist(Post post, StepwisePublisher platform, String token, Instant now) throws SQLException {
        if (post.playlistId() != null) {
            charge(post, QuotaBudget.PLAYLIST_ITEMS_INSERT, now, PostStep.PLAYLIST);
            try {
                platform.addToPlaylist(token, post.playlistId(), post.externalId());
            } catch (PlatformException failure) {
                throw new StepStop(new Stop(PostStep.PLAYLIST, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                        null));
            }
        }
        transactions.inTransaction(c -> {
            writeStep(c, post.id(), PostStep.PLAYLIST, StepState.DONE, now, null);
            return null;
        });
    }

    /**
     * The custom thumbnail, set once the platform has processed the video. Returns false while the video is still processing,
     * so the step waits; a video whose processing failed for good leaves the thumbnail failed and does not block the post.
     */
    private boolean thumbnail(Post post, StepwisePublisher platform, String token, Instant now) throws SQLException {
        if (post.coverAssetId() == null) {
            writeDone(post.id(), PostStep.THUMBNAIL, now);
            return true;
        }
        charge(post, QuotaBudget.VIDEOS_LIST, now, PostStep.THUMBNAIL);
        StepwisePublisher.VideoState state;
        try {
            state = platform.video(token, post.externalId());
        } catch (PlatformException failure) {
            throw new StepStop(new Stop(PostStep.THUMBNAIL, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                    null));
        }
        if (state.processingFailed()) {
            transactions.inTransaction(c -> {
                writeStep(c, post.id(), PostStep.THUMBNAIL, StepState.FAILED, now, THUMBNAIL_FAILED);
                return null;
            });
            return true;
        }
        if (!state.processed()) {
            return false;
        }
        FinishedAsset cover = transactions.inTransaction(c -> posts.finishedAsset(c, post.coverAssetId()))
                .orElseThrow(() -> new StepStop(new Stop(PostStep.THUMBNAIL, "The thumbnail asset is missing.", false, null)));
        String type = thumbnailTypeOf(cover.format());
        if (type == null) {
            throw new StepStop(new Stop(PostStep.THUMBNAIL, THUMBNAIL_FORMAT, false, null));
        }
        if (cover.sizeBytes() > THUMBNAIL_MAX_BYTES) {
            throw new StepStop(new Stop(PostStep.THUMBNAIL, "The thumbnail is larger than the platform accepts.", false, null));
        }
        charge(post, QuotaBudget.THUMBNAILS_SET, now, PostStep.THUMBNAIL);
        try {
            byte[] image = stepwise.media().read(cover.storageKey(), 0, (int) cover.sizeBytes());
            platform.setThumbnail(token, post.externalId(), type, image);
        } catch (IOException unreadable) {
            throw new StepStop(new Stop(PostStep.THUMBNAIL, "The thumbnail could not be read.", true, null));
        } catch (PlatformException failure) {
            throw new StepStop(new Stop(PostStep.THUMBNAIL, reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                    null));
        }
        writeDone(post.id(), PostStep.THUMBNAIL, now);
        return true;
    }

    /**
     * Whether a finish attempt would only wait for a thumbnail that the platform is still processing. Such a wait does not
     * count as an attempt, so a slow video never fails its post; the read costs one unit of quota.
     */
    private boolean onlyThumbnailWaits(Claim claim, Instant now) throws SQLException {
        Post post = claim.post();
        Set<PostStep> open = transactions.inTransaction(c -> openSteps(c, post.id()));
        if (!open.equals(EnumSet.of(PostStep.THUMBNAIL))) {
            return false;
        }
        try {
            StepwisePublisher platform = platforms.require(claim.channel().platform()).stepwise().orElseThrow();
            String token = connections.freshGrant(post.channelId()).accessToken();
            charge(post, QuotaBudget.VIDEOS_LIST, now, PostStep.THUMBNAIL);
            StepwisePublisher.VideoState state = platform.video(token, post.externalId());
            if (state.processed() || state.processingFailed()) {
                return false;
            }
        } catch (ProblemException | PlatformException | StepStop unreadable) {
            // The read could not be made; the post waits for the next run without an attempt.
        }
        transactions.inTransaction(c -> {
            posts.update(c, posts.find(c, post.id()).orElseThrow().released(now));
            return null;
        });
        return true;
    }

    /** The steps of a post that are done. */
    private Set<PostStep> doneSteps(Connection c, java.util.UUID postId) throws SQLException {
        Set<PostStep> done = EnumSet.noneOf(PostStep.class);
        for (StepRecord step : posts.steps(c, postId)) {
            if (step.state() == StepState.DONE) {
                done.add(step.step());
            }
        }
        return done;
    }

    /** Charges one call of a method to the channel's quota; a refused call defers the post until the quota resets. */
    private void charge(Post post, String method, Instant now, PostStep step) throws SQLException {
        try {
            transactions.inTransaction(c -> {
                stepwise.quota().charge(c, post.channelId(), method, now);
                return null;
            });
        } catch (QuotaLedger.Refusal refused) {
            throw new StepStop(new Stop(step, QUOTA_DELAYED, true, QuotaBudget.nextReset(now)));
        }
    }

    private void saveUpload(java.util.UUID postId, PostUpload upload, Instant now) throws SQLException {
        transactions.inTransaction(c -> {
            posts.putUpload(c, postId, upload, now);
            return null;
        });
    }

    private void writeDone(java.util.UUID postId, PostStep step, Instant now) throws SQLException {
        transactions.inTransaction(c -> {
            writeStep(c, postId, step, StepState.DONE, now, null);
            return null;
        });
    }

    private byte[] sealSession(java.util.UUID postId, String session) {
        return stepwise.cipher().encrypt(session.getBytes(StandardCharsets.UTF_8), sessionAssociatedData(postId));
    }

    private String openSession(java.util.UUID postId, byte[] sealed) {
        return new String(stepwise.cipher().decrypt(sealed, sessionAssociatedData(postId)), StandardCharsets.UTF_8);
    }

    private static byte[] sessionAssociatedData(java.util.UUID postId) {
        return ("youtube-upload-session:" + postId).getBytes(StandardCharsets.UTF_8);
    }

    /** The MIME type of a stored video format, or null when the platform takes no such format. */
    static String contentTypeOf(String format) {
        return switch (format) {
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            default -> null;
        };
    }

    /** The MIME type of a thumbnail format; the platform takes JPEG and PNG only. */
    static String thumbnailTypeOf(String format) {
        return switch (format) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> null;
        };
    }

    /** The post's hashtags without the leading '#', as the platform's tags. */
    static List<String> tagsOf(Post post) {
        return post.hashtags().stream().map(tag -> tag.startsWith("#") ? tag.substring(1) : tag).toList();
    }

    private static String privacyOf(PlatformAdapter.Visibility visibility) {
        return visibility.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Marks the post published and records its platform item as an external post, with the origin of the post. */
    private void markPublished(Connection c, Post current, String platformItemId, String url, Instant now) throws SQLException {
        Post published = posts.update(c, current.published(platformItemId, url, now, now));
        writeStep(c, published.id(), PostStep.UPLOAD, StepState.DONE, now, null);
        writeStep(c, published.id(), PostStep.METADATA, StepState.DONE, now, null);
        writeStep(c, published.id(), PostStep.PUBLISH, StepState.DONE, now, null);
        Channel channel = channels.findChannel(c, published.channelId()).orElseThrow();
        PlatformAdapter.PublishedItem item = new PlatformAdapter.PublishedItem(platformItemId, url, published.title(), now,
                "video");
        ExternalPost external = ExternalPost.create(identifiers.next(), channel.id(), channel.platform(), item, now);
        externalPosts.insert(c, external);
        listener.imported(c, external, item);
        posts.insertOrigin(c, external.id(), published.id(), posts.lastAcceptedRevision(c, published.id()).orElse(null));
    }

    /** The row of the attempt that only waited is removed, so the history shows the attempts that counted. */
    private void revokeAttempt(Connection c, Post current) throws SQLException {
        posts.deleteAttempt(c, current.id(), current.attempts());
    }

    private void endAttempt(Connection c, java.util.UUID postId, String outcome, String reason, Instant now)
            throws SQLException {
        List<AttemptRecord> attempts = posts.attempts(c, postId);
        for (AttemptRecord attempt : attempts) {
            if (attempt.finishedAt() == null) {
                posts.putAttempt(c, new AttemptRecord(postId, attempt.attempt(), attempt.startedAt(), now, outcome, reason));
            }
        }
    }

    /** Every step the attempt has not finished becomes failed with the reason; finished steps keep their state. */
    private void failSteps(Connection c, java.util.UUID postId, String reason, Instant now) throws SQLException {
        for (StepRecord step : posts.steps(c, postId)) {
            if (step.state() != StepState.DONE) {
                writeStep(c, postId, step.step(), StepState.FAILED, now, reason);
            }
        }
    }

    private void writeStep(Connection c, java.util.UUID postId, PostStep step, StepState state, Instant now, String reason)
            throws SQLException {
        posts.putStep(c, new StepRecord(postId, step, state, now, reason));
    }

    // ---------------------------------------------------------------- confirm

    /** A handed-over post whose time has come: the platform's status decides whether it is published now. */
    private Outcome confirm(Claim claim, Instant now) throws SQLException {
        Post post = claim.post();
        PlatformAdapter adapter = platforms.require(claim.channel().platform());
        Result result;
        try {
            String token = connections.freshGrant(post.channelId()).accessToken();
            PublicationStatus status = adapter.status(token, post.externalId());
            result = switch (status) {
                case PUBLISHED -> Result.published(post.externalId(), post.externalUrl());
                case FAILED, REMOVED -> Result.failure(NOT_PUBLISHED, false, false);
                case PENDING, PROCESSING -> Result.still();
            };
        } catch (ProblemException | PlatformException | PlatformAdapter.Unsupported waitingFor) {
            result = Result.still();
        }
        Result decided = result;
        if (decided.done()) {
            return transactions.inTransaction(c -> {
                Post current = posts.find(c, post.id()).orElseThrow();
                markPublished(c, current, decided.platformItemId(), decided.url(), now);
                return Outcome.PUBLISHED;
            });
        }
        if (decided.waiting()) {
            return transactions.inTransaction(c -> {
                posts.update(c, posts.find(c, post.id()).orElseThrow().released(now));
                return Outcome.WAITING;
            });
        }
        return transactions.inTransaction(c -> {
            Post current = posts.find(c, post.id()).orElseThrow();
            posts.update(c, current.failed(decided.reason(), now));
            failSteps(c, current.id(), decided.reason(), now);
            return Outcome.FAILED;
        });
    }

    private static String reasonOf(PlatformException failure) {
        return switch (failure.kind()) {
            case RATE_LIMITED -> RATE_LIMITED;
            case REJECTED -> REFUSED;
            case UNAVAILABLE -> UNREACHABLE;
        };
    }
}
