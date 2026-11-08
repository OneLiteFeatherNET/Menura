package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.SecretHasher;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The connection layer of channels: consent through the platform, grants and their refresh, disconnect with
 * revocation, connection health, quota and rate-limit state, the audit status of each platform app, and the listing and
 * metrics the adapters offer. Every token stays inside this service and the store; nothing it returns carries one.
 *
 * <p>A platform call never runs while a database transaction is open, except the refresh, which holds the channel's
 * row lock so two refreshes cannot rotate the same refresh token twice.
 */
public final class ConnectionService {

    /** How long a consent may take from start to callback. */
    public static final Duration FLOW_LIFETIME = Duration.ofMinutes(10);

    /** The rate limit applied when a platform's Retry-After is missing. */
    static final Duration DEFAULT_RATE_LIMIT = Duration.ofMinutes(15);

    private static final int SOURCE_MAX = 200;
    private static final Pattern UNSAFE_TEXT = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final SecretGenerator secrets;
    private final ChannelStore store;
    private final ChannelAudit audit;
    private final PlatformRegistry platforms;
    private final Duration refreshMargin;
    private final double quotaWarningRatio;

    /**
     * @param refreshMargin      a grant whose access token expires within this margin is refreshed
     * @param quotaWarningRatio  the share of a daily quota (0 to 1) above which the channel view warns
     */
    public ConnectionService(Transactions transactions, Clock clock, IdentifierGenerator identifiers,
                             SecretGenerator secrets, ChannelStore store, ChannelAudit audit,
                             PlatformRegistry platforms, Duration refreshMargin, double quotaWarningRatio) {
        this.transactions = transactions;
        this.clock = clock;
        this.identifiers = identifiers;
        this.secrets = secrets;
        this.store = store;
        this.audit = audit;
        this.platforms = platforms;
        this.refreshMargin = refreshMargin;
        this.quotaWarningRatio = quotaWarningRatio;
    }

    // ---------------------------------------------------------------- consent

    /**
     * Starts a consent. Without a channel id a new channel is connected; with one, the consent reconnects that channel
     * or enables further features on it. Returns the platform's own consent URL, which carries the state and PKCE
     * challenge of this flow only.
     */
    public URI startConsent(Actor actor, String platform, Set<String> features, UUID channelId) throws SQLException {
        PlatformAdapter adapter = platforms.require(platform);
        Set<String> requested = featuresOf(adapter, features);
        if (channelId != null) {
            Channel channel = transactions.inTransaction(c -> store.findChannel(c, channelId)).orElseThrow(this::notFound);
            if (!channel.platform().equals(platform)) {
                throw new ProblemException(400, "Bad Request", "The channel belongs to another platform.");
            }
            if (channel.state() == ConnectionState.DISCONNECTED) {
                throw new ProblemException(409, "Conflict", "The channel is disconnected. Connect it again as a new channel.");
            }
            if (requested.isEmpty()) {
                requested = channel.features();
            }
        }
        String state = secrets.next();
        String verifier = Pkce.newVerifier();
        Instant expiresAt = clock.instant().plus(FLOW_LIFETIME);
        Set<String> enabled = Set.copyOf(requested);
        transactions.inTransaction(c -> {
            store.insertFlow(c, new ChannelStore.ConnectionFlow(SecretHasher.hash(state), actor.personId(), platform,
                    enabled, channelId, verifier, expiresAt));
            return null;
        });
        return adapter.authorizationUri(new PlatformAdapter.ConsentRequest(state, Pkce.s256Challenge(verifier),
                adapter.redirectUri(), adapter.scopesFor(requested)));
    }

    /**
     * Completes a consent from the platform's callback. The state is used once; a state that is unknown, expired or
     * started by another person is refused. A refused or cancelled consent changes nothing.
     */
    public ConsentOutcome completeConsent(Actor actor, String state, String code, String error) throws SQLException {
        if (state == null || state.isBlank()) {
            throw unknownConsent();
        }
        Instant now = clock.instant();
        ChannelStore.ConnectionFlow flow = transactions.inTransaction(c -> store.takeFlow(c, SecretHasher.hash(state)))
                .orElseThrow(ConnectionService::unknownConsent);
        if (!now.isBefore(flow.expiresAt())) {
            throw unknownConsent();
        }
        if (!flow.personId().equals(actor.personId())) {
            throw new ProblemException(400, "Bad Request", "The consent belongs to another sign-in. Start it again.");
        }
        if (error != null) {
            return ConsentOutcome.refused(refusalReason(error));
        }
        if (code == null || code.isBlank()) {
            throw new ProblemException(400, "Bad Request", "The platform returned no authorization code.");
        }
        PlatformAdapter adapter = platforms.require(flow.platform());
        PlatformAdapter.TokenSet tokens;
        PlatformAdapter.AccountIdentity identity;
        try {
            tokens = adapter.exchangeCode(code, flow.codeVerifier(), adapter.redirectUri());
            identity = adapter.identify(tokens.accessToken()).orElse(null);
        } catch (PlatformException failure) {
            if (failure.kind() == PlatformException.Kind.REJECTED) {
                return ConsentOutcome.refused("The platform rejected the consent. Start it again.");
            }
            throw platformFailure(failure);
        }
        if (identity == null) {
            discard(adapter, tokens);
            return ConsentOutcome.refused("This account has no channel on the platform.");
        }
        ConsentOutcome outcome = transactions.inTransaction(c -> flow.channelId() == null
                ? connectNew(c, actor, flow, identity, tokens, now)
                : reconnect(c, actor, flow, identity, tokens, now));
        if (outcome.kind() == ConsentOutcome.Kind.ALREADY_CONNECTED || outcome.kind() == ConsentOutcome.Kind.ACCOUNT_MISMATCH) {
            discard(adapter, tokens);
        }
        return outcome;
    }

    private ConsentOutcome connectNew(Connection c, Actor actor, ChannelStore.ConnectionFlow flow,
                                      PlatformAdapter.AccountIdentity identity, PlatformAdapter.TokenSet tokens,
                                      Instant now) throws SQLException {
        Optional<Channel> existing = store.findActiveByAccount(c, flow.platform(), identity.platformAccountId());
        if (existing.isPresent()) {
            return ConsentOutcome.alreadyConnected(existing.get().id());
        }
        UUID id = identifiers.next();
        Channel channel = new Channel(id, flow.platform(), identity.platformAccountId(), identity.displayName(),
                ConnectionState.CONNECTED, null, flow.features(), tokens.scopes(), null, now, now, null);
        store.insertChannel(c, channel);
        store.saveGrant(c, grantOf(id, tokens, now));
        audit.record(c, actor, "channel.connect", channelTarget(id));
        return ConsentOutcome.connected(id);
    }

    private ConsentOutcome reconnect(Connection c, Actor actor, ChannelStore.ConnectionFlow flow,
                                     PlatformAdapter.AccountIdentity identity, PlatformAdapter.TokenSet tokens,
                                     Instant now) throws SQLException {
        Channel channel = store.lockChannel(c, flow.channelId()).orElseThrow(this::notFound);
        if (channel.state() == ConnectionState.DISCONNECTED) {
            return ConsentOutcome.refused("The channel was disconnected. Connect it again as a new channel.");
        }
        if (!channel.platformAccountId().equals(identity.platformAccountId())) {
            return ConsentOutcome.accountMismatch(channel.id());
        }
        Channel updated = channel.state() == ConnectionState.NEEDS_RECONNECTION
                ? channel.reconnected(tokens.scopes(), flow.features(), now)
                : channel.scopesChanged(tokens.scopes(), flow.features(), now);
        store.saveGrant(c, grantOf(channel.id(), tokens, now));
        store.updateChannel(c, updated);
        audit.record(c, actor, "channel.reconnect", channelTarget(channel.id()));
        return ConsentOutcome.connected(channel.id());
    }

    // ---------------------------------------------------------- refresh

    /**
     * The grant a publication may use. A token that expires within the refresh margin is refreshed first; a channel
     * that needs reconnection, or a rejected refresh, refuses the publication with 409 and no request is sent.
     */
    public ChannelGrant freshGrant(UUID channelId) throws SQLException {
        Instant now = clock.instant();
        Result result = transactions.inTransaction(c -> {
            Channel channel = store.lockChannel(c, channelId).orElseThrow(this::notFound);
            if (!channel.usable()) {
                return Result.failure(unusable(channel));
            }
            ChannelGrant grant = store.findGrant(c, channelId).orElse(null);
            if (grant == null) {
                markNeedsReconnection(c, channel, NO_GRANT_REASON, now);
                return Result.failure(reconnectionRequired());
            }
            return isDue(grant, now) ? refreshLocked(c, channel, grant, now) : Result.current(grant);
        });
        if (result.failure != null) {
            throw result.failure;
        }
        return result.grant;
    }

    /**
     * The scheduled health check: refreshes every connected channel whose access token expires within the margin,
     * marks rejected grants as needing reconnection, and leaves channels alone when the platform cannot be reached.
     * Each grant is rewritten with the primary encryption key, which is how a key rotation takes effect.
     */
    public HealthReport checkHealth() throws SQLException {
        Instant now = clock.instant();
        List<UUID> ids = transactions.inTransaction(c -> store.listInState(c, ConnectionState.CONNECTED))
                .stream().map(Channel::id).toList();
        int refreshed = 0;
        int needsReconnection = 0;
        int unavailable = 0;
        for (UUID id : ids) {
            Result result = transactions.inTransaction(c -> {
                Optional<Channel> channel = store.lockChannel(c, id);
                if (channel.isEmpty() || !channel.get().usable()) {
                    return Result.failure(null);
                }
                ChannelGrant grant = store.findGrant(c, id).orElse(null);
                if (grant == null) {
                    markNeedsReconnection(c, channel.get(), NO_GRANT_REASON, now);
                    return Result.needsReconnection(reconnectionRequired());
                }
                if (!isDue(grant, now)) {
                    store.saveGrant(c, grant);
                    return Result.current(grant);
                }
                return refreshLocked(c, channel.get(), grant, now);
            });
            switch (result.outcome) {
                case REFRESHED -> refreshed++;
                case NEEDS_RECONNECTION -> needsReconnection++;
                case UNAVAILABLE -> unavailable++;
                default -> {
                }
            }
        }
        return new HealthReport(ids.size(), refreshed, needsReconnection, unavailable);
    }

    /** Refreshes one grant inside the caller's transaction, with the channel row already locked. */
    private Result refreshLocked(Connection c, Channel channel, ChannelGrant grant, Instant now) throws SQLException {
        Optional<PlatformAdapter> adapter = platforms.find(channel.platform());
        if (adapter.isEmpty()) {
            return Result.unavailable(platformUnavailable());
        }
        if (grant.refreshToken() == null) {
            markNeedsReconnection(c, channel, NO_REFRESH_TOKEN_REASON, now);
            return Result.needsReconnection(reconnectionRequired());
        }
        try {
            PlatformAdapter.TokenSet tokens = adapter.get().refresh(grant.refreshToken());
            String rotated = tokens.refreshToken() != null ? tokens.refreshToken() : grant.refreshToken();
            ChannelGrant refreshed = new ChannelGrant(channel.id(), tokens.accessToken(), rotated, tokens.accessExpiresAt(), now);
            store.saveGrant(c, refreshed);
            return Result.refreshed(refreshed);
        } catch (PlatformException failure) {
            return switch (failure.kind()) {
                case REJECTED -> {
                    markNeedsReconnection(c, channel, REJECTED_REASON, now);
                    yield Result.needsReconnection(reconnectionRequired());
                }
                case RATE_LIMITED -> {
                    Duration wait = failure.retryAfter() == null ? DEFAULT_RATE_LIMIT : failure.retryAfter();
                    recordRateLimit(c, channel.id(), now.plus(wait), RATE_LIMIT_REASON);
                    yield Result.unavailable(platformUnavailable());
                }
                case UNAVAILABLE -> Result.unavailable(platformUnavailable());
            };
        } catch (PlatformAdapter.Unsupported unsupported) {
            return Result.unavailable(platformUnavailable());
        }
    }

    private void markNeedsReconnection(Connection c, Channel channel, String reason, Instant now) throws SQLException {
        store.updateChannel(c, channel.needsReconnection(reason, now));
    }

    private boolean isDue(ChannelGrant grant, Instant now) {
        return grant.accessExpiresAt() == null || !now.plus(refreshMargin).isBefore(grant.accessExpiresAt());
    }

    // -------------------------------------------------------- disconnect

    /**
     * Disconnects a channel. The platform revocation is requested where the platform offers it; the stored tokens are
     * deleted in every case, and the record says whether the platform grant may still exist. Returns the revocation
     * outcome: {@code revoked}, {@code not_supported} or {@code failed}.
     */
    public String disconnect(Actor actor, UUID channelId) throws SQLException {
        Channel channel = transactions.inTransaction(c -> store.lockChannel(c, channelId)).orElseThrow(this::notFound);
        if (channel.state() == ConnectionState.DISCONNECTED) {
            throw new ProblemException(409, "Conflict", "The channel is already disconnected.");
        }
        ChannelGrant grant = transactions.inTransaction(c -> store.findGrant(c, channelId)).orElse(null);
        String revocation = revoke(platforms.find(channel.platform()).orElse(null), grant);
        Instant now = clock.instant();
        transactions.inTransaction(c -> {
            store.deleteGrant(c, channelId);
            store.updateChannel(c, channel.disconnected(revocation, now));
            audit.record(c, actor, "channel.disconnect", channelTarget(channelId));
            return null;
        });
        return revocation;
    }

    private static String revoke(PlatformAdapter adapter, ChannelGrant grant) {
        if (adapter == null || !adapter.capabilities().revocation()) {
            return "not_supported";
        }
        if (grant == null) {
            return "failed";
        }
        String token = grant.refreshToken() != null ? grant.refreshToken() : grant.accessToken();
        try {
            adapter.revoke(token);
            return "revoked";
        } catch (RuntimeException failure) {
            return "failed";
        }
    }

    // ----------------------------------------------------- quota and limits

    /** Records the quota the platform reported or Menura measured for a channel. */
    public void recordQuota(UUID channelId, long used, long limit, Instant resetAt) throws SQLException {
        if (used < 0 || limit <= 0) {
            throw new ProblemException(400, "Bad Request", "The quota values are out of range.");
        }
        Instant now = clock.instant();
        transactions.inTransaction(c -> {
            requireChannel(c, channelId);
            ChannelStore.QuotaState current = store.findQuota(c, channelId).orElse(null);
            store.saveQuota(c, new ChannelStore.QuotaState(channelId, used, limit, resetAt, now,
                    current == null ? null : current.rateLimitedUntil(), current == null ? null : current.rateLimitReason()));
            return null;
        });
    }

    /** Records that a platform rate limit delayed publications on the channel until {@code until}. */
    public void recordRateLimit(UUID channelId, Instant until, String reason) throws SQLException {
        requireSafe(reason, 200, "The reason");
        transactions.inTransaction(c -> {
            requireChannel(c, channelId);
            recordRateLimit(c, channelId, until, reason);
            return null;
        });
    }

    private void recordRateLimit(Connection c, UUID channelId, Instant until, String reason) throws SQLException {
        ChannelStore.QuotaState current = store.findQuota(c, channelId).orElse(null);
        store.saveQuota(c, new ChannelStore.QuotaState(channelId,
                current == null ? null : current.quotaUsed(), current == null ? null : current.quotaLimit(),
                current == null ? null : current.quotaResetAt(), current == null ? null : current.measuredAt(),
                until, reason));
    }

    // --------------------------------------------------------- audit status

    /** Records the audit status of a platform app, with its date and source, and audits the decision. */
    public void setAuditStatus(Actor actor, String platform, AuditStatus status, String source) throws SQLException {
        platforms.require(platform);
        requireSafe(source, SOURCE_MAX, "The source");
        Instant now = clock.instant();
        transactions.inTransaction(c -> {
            store.saveAuditStatus(c, new ChannelStore.PlatformAuditStatus(platform, status, now, source));
            audit.record(c, actor, "platform_audit_status.set", "platform:" + platform);
            return null;
        });
    }

    /** Whether public visibility may be used on this platform. Only an audited app allows it. */
    public boolean publicVisibilityAllowed(String platform) throws SQLException {
        return auditStatus(platform).status() == AuditStatus.AUDITED;
    }

    // ----------------------------------------------------- read models

    /** One channel with what its page shows. */
    public ChannelSnapshot snapshot(UUID channelId) throws SQLException {
        Instant now = clock.instant();
        return transactions.inTransaction(c -> {
            Channel channel = store.findChannel(c, channelId).orElseThrow(this::notFound);
            return snapshotOf(c, channel, now);
        });
    }

    /** Channels in id order after {@code afterId}, at most {@code limit}. */
    public List<ChannelSnapshot> list(UUID afterId, int limit) throws SQLException {
        Instant now = clock.instant();
        return transactions.inTransaction(c -> {
            List<ChannelSnapshot> snapshots = new ArrayList<>();
            for (Channel channel : store.listChannels(c, afterId, limit)) {
                snapshots.add(snapshotOf(c, channel, now));
            }
            return snapshots;
        });
    }

    /** Every platform of this build with its capabilities, features and audit status. */
    public List<PlatformSnapshot> platforms() throws SQLException {
        List<PlatformSnapshot> out = new ArrayList<>();
        for (PlatformAdapter adapter : platforms.all()) {
            out.add(new PlatformSnapshot(adapter.platform(), adapter.displayName(), adapter.capabilities(),
                    adapter.features(), auditViewOf(auditStatus(adapter.platform()))));
        }
        return out;
    }

    // ----------------------------------------------- scheduling and features

    /** Refuses scheduling on a channel whose platform has no native scheduling. */
    public void requireScheduling(UUID channelId) throws SQLException {
        PlatformAdapter adapter = adapterOf(channelId);
        if (!adapter.capabilities().nativeScheduling()) {
            throw new ProblemException(422, "Unprocessable Entity",
                    "The platform does not support scheduled publishing for this channel.");
        }
    }

    /**
     * Refuses use of a feature that the channel is not enabled for, or whose scopes were not granted. Used before a
     * publication or an import uses the feature.
     */
    public void requireFeature(UUID channelId, String feature) throws SQLException {
        Channel channel = transactions.inTransaction(c -> store.findChannel(c, channelId)).orElseThrow(this::notFound);
        if (!channel.usable()) {
            throw unusable(channel);
        }
        if (!channel.features().contains(feature)) {
            throw new ProblemException(409, "Conflict", "The feature is not enabled for this channel.");
        }
        PlatformAdapter adapter = adapterOf(channel);
        for (PlatformFeature declared : adapter.features()) {
            if (declared.name().equals(feature) && !channel.grantedScopes().containsAll(declared.scopes())) {
                throw new ProblemException(409, "Conflict",
                        "The channel is missing a scope for this feature. Connect it again with the feature enabled.");
            }
        }
    }

    // ----------------------------------------------- listing and metrics

    /**
     * The channel's own published items as the platform lists them. Items without a platform identifier are dropped;
     * nothing the platform did not list is added.
     */
    public List<PlatformAdapter.PublishedItem> listPublished(UUID channelId, int limit) throws SQLException {
        PlatformAdapter adapter = adapterOf(channelId);
        if (!adapter.capabilities().listing()) {
            throw new ProblemException(501, "Not Implemented", "Listing published items is not available for this platform.");
        }
        String token = freshGrant(channelId).accessToken();
        try {
            return adapter.listPublished(token, limit).stream()
                    .filter(item -> item.platformItemId() != null && !item.platformItemId().isBlank())
                    .toList();
        } catch (PlatformException failure) {
            throw platformFailure(failure);
        } catch (PlatformAdapter.Unsupported unsupported) {
            throw new ProblemException(501, "Not Implemented", "Listing published items is not available for this platform.");
        }
    }

    /**
     * The requested metrics of one item. A metric the platform does not expose is empty, never zero, so analytics
     * records it as unavailable.
     */
    public Map<String, OptionalLong> readMetrics(UUID channelId, String platformItemId, Set<String> metrics) throws SQLException {
        PlatformAdapter adapter = adapterOf(channelId);
        Map<String, Long> values = Map.of();
        if (adapter.capabilities().metrics()) {
            String token = freshGrant(channelId).accessToken();
            try {
                values = adapter.readMetrics(token, platformItemId, metrics);
            } catch (PlatformException failure) {
                throw platformFailure(failure);
            } catch (PlatformAdapter.Unsupported unsupported) {
                values = Map.of();
            }
        }
        Map<String, OptionalLong> readings = new java.util.HashMap<>();
        for (String metric : metrics) {
            Long value = values.get(metric);
            readings.put(metric, value == null ? OptionalLong.empty() : OptionalLong.of(value));
        }
        return readings;
    }

    // ----------------------------------------------------------- helpers

    private ChannelSnapshot snapshotOf(Connection c, Channel channel, Instant now) throws SQLException {
        ChannelStore.QuotaState quota = store.findQuota(c, channel.id()).orElse(null);
        return new ChannelSnapshot(channel, missingScopes(channel), quotaView(quota, now),
                auditViewOf(auditStatus(c, channel.platform())));
    }

    private Set<String> missingScopes(Channel channel) {
        return platforms.find(channel.platform()).map(adapter -> {
            Set<String> missing = new HashSet<>(adapter.scopesFor(channel.features()));
            missing.removeAll(channel.grantedScopes());
            return Set.copyOf(missing);
        }).orElse(Set.of());
    }

    private ChannelSnapshot.QuotaView quotaView(ChannelStore.QuotaState quota, Instant now) {
        if (quota == null) {
            return new ChannelSnapshot.QuotaView(null, null, null, false, null);
        }
        boolean warning = quota.quotaUsed() != null && quota.quotaLimit() != null
                && quota.quotaUsed() >= quotaWarningRatio * quota.quotaLimit();
        ChannelSnapshot.Delay delay = quota.rateLimitedUntil() != null && quota.rateLimitedUntil().isAfter(now)
                ? new ChannelSnapshot.Delay(quota.rateLimitReason(), quota.rateLimitedUntil()) : null;
        return new ChannelSnapshot.QuotaView(quota.quotaUsed(), quota.quotaLimit(), quota.quotaResetAt(), warning, delay);
    }

    private ChannelStore.PlatformAuditStatus auditStatus(String platform) throws SQLException {
        return transactions.inTransaction(c -> auditStatus(c, platform));
    }

    private ChannelStore.PlatformAuditStatus auditStatus(Connection c, String platform) throws SQLException {
        return store.findAuditStatus(c, platform)
                .orElse(new ChannelStore.PlatformAuditStatus(platform, AuditStatus.UNKNOWN, null, null));
    }

    private static ChannelSnapshot.AuditView auditViewOf(ChannelStore.PlatformAuditStatus status) {
        return new ChannelSnapshot.AuditView(status.status(), status.status().displayText(), status.status().restrictions(),
                status.decidedAt(), status.source());
    }

    private PlatformAdapter adapterOf(UUID channelId) throws SQLException {
        Channel channel = transactions.inTransaction(c -> store.findChannel(c, channelId)).orElseThrow(this::notFound);
        return adapterOf(channel);
    }

    private PlatformAdapter adapterOf(Channel channel) {
        return platforms.require(channel.platform());
    }

    private void requireChannel(Connection c, UUID channelId) throws SQLException {
        if (store.findChannel(c, channelId).isEmpty()) {
            throw notFound();
        }
    }

    private Set<String> featuresOf(PlatformAdapter adapter, Set<String> requested) {
        Set<String> declared = new HashSet<>();
        for (PlatformFeature feature : adapter.features()) {
            declared.add(feature.name());
        }
        Set<String> chosen = requested == null ? Set.of() : requested;
        for (String feature : chosen) {
            if (!declared.contains(feature)) {
                throw new ProblemException(400, "Bad Request", "The platform does not offer one of the requested features.");
            }
        }
        return Set.copyOf(chosen);
    }

    private static ChannelGrant grantOf(UUID channelId, PlatformAdapter.TokenSet tokens, Instant now) {
        return new ChannelGrant(channelId, tokens.accessToken(), tokens.refreshToken(), tokens.accessExpiresAt(), now);
    }

    /** Discards tokens of a consent whose outcome did not keep them, so no unused live grant remains at the platform. */
    private static void discard(PlatformAdapter adapter, PlatformAdapter.TokenSet tokens) {
        revoke(adapter, new ChannelGrant(null, tokens.accessToken(), tokens.refreshToken(), null, null));
    }

    private static String refusalReason(String error) {
        return "access_denied".equals(error)
                ? "The consent was refused or cancelled at the platform."
                : "The platform did not complete the consent. Start it again.";
    }

    private static ProblemException platformFailure(PlatformException failure) {
        if (failure.kind() == PlatformException.Kind.RATE_LIMITED) {
            return new ProblemException(503, "Service Unavailable", "The platform is rate limiting requests. Try again later.");
        }
        return new ProblemException(502, "Bad Gateway", "The platform could not complete the request. Try again later.");
    }

    private static ProblemException platformUnavailable() {
        return new ProblemException(503, "Service Unavailable", "The platform could not be reached. Try again later.");
    }

    private static ProblemException reconnectionRequired() {
        return new ProblemException(409, "Conflict", "The channel needs reconnection before it can be used.");
    }

    private static ProblemException unusable(Channel channel) {
        return new ProblemException(409, "Conflict", "The channel is " + channel.state().value().replace('_', ' ')
                + " and cannot be used.");
    }

    private ProblemException notFound() {
        return new ProblemException(404, "Not Found", "The channel does not exist.");
    }

    private static ProblemException unknownConsent() {
        return new ProblemException(400, "Bad Request",
                "The consent is unknown, has expired or was already used. Start it again.");
    }

    private static String channelTarget(UUID id) {
        return "channel:" + id;
    }

    private static void requireSafe(String value, int max, String what) {
        if (value == null || value.isBlank() || value.length() > max || UNSAFE_TEXT.matcher(value).find()) {
            throw new ProblemException(400, "Bad Request",
                    what + " must be a short text of at most " + max + " characters without control characters.");
        }
    }

    static final String NO_GRANT_REASON = "no grant is stored; reconnect the channel";
    static final String NO_REFRESH_TOKEN_REASON = "no refresh token was granted; reconnect the channel";
    static final String REJECTED_REASON = "the platform rejected the refresh; reconnect the channel";
    static final String RATE_LIMIT_REASON = "platform rate limit on token refresh";

    /** The counts of one health run. */
    public record HealthReport(int checked, int refreshed, int needsReconnection, int unavailable) {
    }

    private enum Outcome { CURRENT, REFRESHED, NEEDS_RECONNECTION, UNAVAILABLE, SKIPPED }

    /** The result of one step under a lock: the grant to use, or the refusal to report after the commit. */
    private static final class Result {
        final Outcome outcome;
        final ChannelGrant grant;
        final ProblemException failure;

        private Result(Outcome outcome, ChannelGrant grant, ProblemException failure) {
            this.outcome = outcome;
            this.grant = grant;
            this.failure = failure;
        }

        static Result current(ChannelGrant grant) {
            return new Result(Outcome.CURRENT, grant, null);
        }

        static Result refreshed(ChannelGrant grant) {
            return new Result(Outcome.REFRESHED, grant, null);
        }

        static Result needsReconnection(ProblemException failure) {
            return new Result(Outcome.NEEDS_RECONNECTION, null, failure);
        }

        static Result unavailable(ProblemException failure) {
            return new Result(Outcome.UNAVAILABLE, null, failure);
        }

        static Result failure(ProblemException failure) {
            return new Result(Outcome.SKIPPED, null, failure);
        }
    }
}
