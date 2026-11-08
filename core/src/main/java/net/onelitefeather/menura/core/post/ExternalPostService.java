package net.onelitefeather.menura.core.post;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPostStore.SyncStatus;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Imports the existing posts of connected channels and re-syncs them: new items are added, changed items updated, items
 * a complete listing no longer contains are marked removed. Records are never deleted.
 *
 * <p>The listing comes from the platform adapter's listing operation, with a fresh grant from the connection service. A
 * channel that needs reconnection is not read and is reported as waiting. A listing that fails, or that reaches the
 * limit, is incomplete: it adds what it lists but marks nothing removed.
 */
public final class ExternalPostService {

    /** The most items one listing asks for. A listing that reaches it is incomplete and removes nothing. */
    public static final int LISTING_LIMIT = 5000;

    static final String NOT_LISTED = "The platform does not list published items in this build.";
    static final String UNREACHABLE = "The platform could not be reached.";
    static final String RATE_LIMITED = "The platform is rate limiting requests.";
    static final String REFUSED = "The platform refused the request. The channel may need reconnection.";
    static final String FAILED = "The platform could not complete the listing.";
    static final String INCOMPLETE = "The listing reached its limit; posts that were not listed were not marked removed.";

    private final Transactions transactions;
    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final ChannelStore channels;
    private final ExternalPostStore posts;
    private final ConnectionService connections;
    private final PlatformRegistry platforms;
    private final int listingLimit;

    public ExternalPostService(Transactions transactions, Clock clock, IdentifierGenerator identifiers,
                               ChannelStore channels, ExternalPostStore posts, ConnectionService connections,
                               PlatformRegistry platforms) {
        this(transactions, clock, identifiers, channels, posts, connections, platforms, LISTING_LIMIT);
    }

    public ExternalPostService(Transactions transactions, Clock clock, IdentifierGenerator identifiers,
                               ChannelStore channels, ExternalPostStore posts, ConnectionService connections,
                               PlatformRegistry platforms, int listingLimit) {
        if (listingLimit < 1) {
            throw new IllegalArgumentException("the listing limit must be at least 1");
        }
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.posts = Objects.requireNonNull(posts, "posts");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.platforms = Objects.requireNonNull(platforms, "platforms");
        this.listingLimit = listingLimit;
    }

    /** Re-syncs one channel now, whatever the time of its last sync. */
    public SyncResult syncChannel(UUID channelId) throws SQLException {
        Channel channel = transactions.inTransaction(c -> channels.findChannel(c, channelId)).orElseThrow(this::notFound);
        if (channel.state() != ConnectionState.CONNECTED) {
            return SyncResult.waiting(channelId);
        }
        Listing listing;
        try {
            listing = fetch(channel);
        } catch (ProblemException refused) {
            if (refused.status() == 409) {
                return SyncResult.waiting(channelId);
            }
            return recordFailure(channelId, FAILED);
        } catch (PlatformException failure) {
            return recordFailure(channelId, reasonOf(failure));
        } catch (PlatformAdapter.Unsupported unsupported) {
            return recordFailure(channelId, NOT_LISTED);
        }
        return apply(channelId, channel.platform(), listing);
    }

    /**
     * The scheduled run: re-syncs every connected channel whose last complete sync is older than {@code minAge}. Channels
     * that need reconnection are counted as waiting and not read.
     */
    public SyncReport syncDue(Duration minAge) throws SQLException {
        Instant now = clock.instant();
        List<Channel> connected = transactions.inTransaction(c -> channels.listInState(c, ConnectionState.CONNECTED));
        int waiting = transactions.inTransaction(c -> channels.listInState(c, ConnectionState.NEEDS_RECONNECTION)).size();
        int synced = 0;
        int skipped = 0;
        int incomplete = 0;
        int failed = 0;
        for (Channel channel : connected) {
            Optional<SyncStatus> status = transactions.inTransaction(c -> posts.findSync(c, channel.id()));
            if (status.isPresent() && status.get().lastSuccessAt() != null
                    && status.get().lastSuccessAt().plus(minAge).isAfter(now)) {
                skipped++;
                continue;
            }
            switch (syncChannel(channel.id()).outcome()) {
                case SYNCED -> synced++;
                case INCOMPLETE -> incomplete++;
                case FAILED -> failed++;
                case WAITING_FOR_RECONNECTION -> waiting++;
            }
        }
        return new SyncReport(connected.size(), synced, skipped, incomplete, failed, waiting);
    }

    /**
     * One page of a channel's posts, newest first, with the sync status of the channel. Throws 404 when the channel does
     * not exist. Pass the publish time and identifier of the last item of the previous page to continue it.
     */
    public Overview overview(UUID channelId, Instant afterPublishedAt, UUID afterId, int limit) throws SQLException {
        return transactions.inTransaction(c -> {
            Channel channel = channels.findChannel(c, channelId).orElseThrow(this::notFound);
            List<ExternalPost> page = posts.page(c, channelId, afterPublishedAt, afterId, limit);
            SyncStatus status = posts.findSync(c, channelId).orElse(null);
            return new Overview(channel, page, status);
        });
    }

    /** The listing of one channel: its items, and whether the listing is complete. */
    private record Listing(List<PublishedItem> items, boolean complete) {
    }

    private Listing fetch(Channel channel) throws SQLException {
        PlatformAdapter adapter = platforms.require(channel.platform());
        if (!adapter.capabilities().listing()) {
            throw new PlatformAdapter.Unsupported("listing published items");
        }
        String token = connections.freshGrant(channel.id()).accessToken();
        List<PublishedItem> items = adapter.listPublished(token, listingLimit);
        return new Listing(items, items.size() < listingLimit);
    }

    private SyncResult apply(UUID channelId, String platform, Listing listing) throws SQLException {
        return transactions.inTransaction(c -> {
            Optional<Channel> locked = channels.lockChannel(c, channelId);
            if (locked.isEmpty() || locked.get().state() != ConnectionState.CONNECTED) {
                return SyncResult.waiting(channelId);
            }
            Instant now = clock.instant();
            List<ExternalPost> stored = posts.listForChannel(c, channelId);
            Plan plan = plan(channelId, platform, stored, listing, now);
            for (ExternalPost post : plan.inserts()) {
                posts.insert(c, post);
            }
            for (ExternalPost post : plan.updates()) {
                posts.update(c, post);
            }
            SyncStatus previous = posts.findSync(c, channelId).orElse(null);
            Instant lastFailure = previous == null ? null : previous.lastFailureAt();
            if (listing.complete()) {
                posts.saveSync(c, new SyncStatus(channelId, now, lastFailure, null));
                return new SyncResult(channelId, Outcome.SYNCED, plan.inserts().size(), plan.listedChanges(),
                        plan.removed(), null);
            }
            posts.saveSync(c, new SyncStatus(channelId, previous == null ? null : previous.lastSuccessAt(), now, INCOMPLETE));
            return new SyncResult(channelId, Outcome.INCOMPLETE, plan.inserts().size(), plan.listedChanges(), 0, INCOMPLETE);
        });
    }

    private SyncResult recordFailure(UUID channelId, String reason) throws SQLException {
        return transactions.inTransaction(c -> {
            Instant now = clock.instant();
            SyncStatus previous = posts.findSync(c, channelId).orElse(null);
            posts.saveSync(c, new SyncStatus(channelId, previous == null ? null : previous.lastSuccessAt(), now, reason));
            return new SyncResult(channelId, Outcome.FAILED, 0, 0, 0, reason);
        });
    }

    /**
     * The writes one listing needs. Items that match a stored record update it when a field changed; items without a
     * stored record are inserted; stored records the listing lacks are marked removed, but only for a complete listing.
     */
    private Plan plan(UUID channelId, String platform, List<ExternalPost> stored, Listing listing, Instant now) {
        Map<String, ExternalPost> byItem = new HashMap<>();
        for (ExternalPost post : stored) {
            byItem.put(post.platformItemId(), post);
        }
        Set<String> listed = new HashSet<>();
        List<ExternalPost> inserts = new ArrayList<>();
        List<ExternalPost> updates = new ArrayList<>();
        int listedChanges = 0;
        for (PublishedItem item : listing.items()) {
            if (item == null || item.platformItemId() == null) {
                continue;
            }
            if (!listed.add(item.platformItemId())) {
                continue;
            }
            ExternalPost existing = byItem.get(item.platformItemId());
            try {
                if (existing == null) {
                    inserts.add(ExternalPost.create(identifiers.next(), channelId, platform, item, now));
                } else {
                    ExternalPost next = existing.listed(item, now);
                    if (next != existing) {
                        updates.add(next);
                        listedChanges++;
                    }
                }
            } catch (IllegalArgumentException | NullPointerException invalid) {
                // An item without the fields a record needs is not stored half-filled. It is still listed by the
                // platform, so the stored record it would have updated is not marked removed.
            }
        }
        int removed = 0;
        if (listing.complete()) {
            for (ExternalPost post : stored) {
                if (!post.removed() && !listed.contains(post.platformItemId())) {
                    updates.add(post.markedRemoved(now));
                    removed++;
                }
            }
        }
        return new Plan(inserts, updates, listedChanges, removed);
    }

    private record Plan(List<ExternalPost> inserts, List<ExternalPost> updates, int listedChanges, int removed) {
    }

    private static String reasonOf(PlatformException failure) {
        return switch (failure.kind()) {
            case RATE_LIMITED -> RATE_LIMITED;
            case REJECTED -> REFUSED;
            case UNAVAILABLE -> UNREACHABLE;
        };
    }

    private ProblemException notFound() {
        return new ProblemException(404, "Not Found", "The channel does not exist.");
    }

    /** What one channel's run did. The reason is a fixed text, never a token or a response body. */
    public record SyncResult(UUID channelId, Outcome outcome, int added, int updated, int removed, String reason) {

        static SyncResult waiting(UUID channelId) {
            return new SyncResult(channelId, Outcome.WAITING_FOR_RECONNECTION, 0, 0, 0, null);
        }
    }

    public enum Outcome { SYNCED, INCOMPLETE, FAILED, WAITING_FOR_RECONNECTION }

    /** What a scheduled run did over all connected channels. */
    public record SyncReport(int checked, int synced, int skipped, int incomplete, int failed, int waiting) {
    }

    /** One page of posts with the channel and its last sync status (null when the channel was never synced). */
    public record Overview(Channel channel, List<ExternalPost> posts, SyncStatus sync) {
    }
}
