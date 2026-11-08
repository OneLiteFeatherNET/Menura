package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublicationStatus;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostListener;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** What one run did. Counts only; no title, token or URL. */
    public record Report(int claimed, int published, int handedOver, int confirmed, int retried, int failed, int waiting) {
    }

    private enum Action { PUBLISH, HANDOVER, CONFIRM }

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

    public PublicationRunner(Transactions transactions, Clock clock, IdentifierGenerator identifiers, PostStore posts,
                             ChannelStore channels, ConnectionService connections, PlatformRegistry platforms,
                             ExternalPostStore externalPosts, ExternalPostListener listener, PublishSettings settings) {
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
                case PUBLISH, HANDOVER -> {
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
                if (post.externalId() == null) {
                    endUnknown(c, post, now);
                    continue;
                }
                post = post.released(now);
            }
            Post due = post;
            Optional<Channel> channel = channels.findChannel(c, due.channelId());
            Optional<Action> action = channel.flatMap(ch -> actionFor(due, ch, now));
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

    private Optional<Action> actionFor(Post post, Channel channel, Instant now) {
        Optional<PlatformAdapter> adapter = platforms.find(channel.platform());
        if (adapter.isEmpty()) {
            return Optional.empty();
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
        Post started = transactions.inTransaction(c -> {
            Post current = posts.find(c, claim.post().id()).orElseThrow();
            int number = current.attempts() + 1;
            Post next = posts.update(c, current.attemptStarted(now));
            posts.putAttempt(c, new AttemptRecord(next.id(), number, now, null, null, null));
            boolean handover = claim.action() == Action.HANDOVER;
            writeStep(c, next.id(), PostStep.UPLOAD, StepState.RUNNING, now, null);
            writeStep(c, next.id(), PostStep.METADATA, handover ? StepState.RUNNING : StepState.PENDING, now, null);
            writeStep(c, next.id(), PostStep.SCHEDULE, handover ? StepState.RUNNING : StepState.PENDING, now, null);
            writeStep(c, next.id(), PostStep.PUBLISH, StepState.PENDING, now, null);
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

        PlatformAdapter adapter = platforms.require(claim.channel().platform());
        String token;
        try {
            token = connections.freshGrant(started.channelId()).accessToken();
        } catch (ProblemException refusal) {
            return finish(started, claim, Result.failure(RECONNECT, false, false), now);
        } catch (PlatformException failure) {
            return finish(started, claim, Result.failure(reasonOf(failure), failure.kind() != PlatformException.Kind.REJECTED,
                    false), now);
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

    // ---------------------------------------------------------------- helpers

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

    private static String reasonOf(PlatformException failure) {
        return switch (failure.kind()) {
            case RATE_LIMITED -> RATE_LIMITED;
            case REJECTED -> REFUSED;
            case UNAVAILABLE -> UNREACHABLE;
        };
    }
}
