package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.MetricStore.CollectionStatus;
import net.onelitefeather.menura.core.analytics.MetricStore.Reading;
import net.onelitefeather.menura.core.analytics.MetricStore.SnapshotValue;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The scheduled collection of metrics of every external post (change add-content-analytics). For each connected channel
 * whose platform reports metrics it reads only the posts that are due (see {@link #isDue}) through the metrics operation of
 * the adapter port, in batches of the platform's batch size, stores the points, refreshes the latest values, and takes the
 * snapshots that are due. A channel with nothing due makes no platform call.
 *
 * <p>Each post is written in its own transaction, so a channel that fails part way keeps the points of the posts before
 * the failure. A channel whose platform does not report metrics is recorded as not supported, never as zero. Nothing is
 * read through a wall-clock wait; the time comes from the injected clock.
 */
public final class MetricCollector {

    /** A post whose latest value is older than this is read again, even when no snapshot offset is due. */
    public static final Duration STALE_AFTER = Duration.ofHours(24);

    static final String UNREACHABLE = "The platform could not be reached.";
    static final String REFUSED = "The platform refused the request. The channel may need reconnection.";
    static final String RATE_LIMITED = "The platform is rate limiting requests.";
    static final String NOT_SUPPORTED = "The platform does not report metrics in this build.";

    static final String COLLECTED = "collected";
    static final String FAILED = "failed";
    static final String NOT_SUPPORTED_OUTCOME = "not_supported";

    private final Transactions transactions;
    private final Clock clock;
    private final MetricStore metrics;
    private final ExternalPostStore posts;
    private final ChannelStore channels;
    private final ConnectionService connections;
    private final PlatformRegistry platforms;

    public MetricCollector(Transactions transactions, Clock clock, MetricStore metrics, ExternalPostStore posts,
                           ChannelStore channels, ConnectionService connections, PlatformRegistry platforms) {
        this.transactions = transactions;
        this.clock = clock;
        this.metrics = metrics;
        this.posts = posts;
        this.channels = channels;
        this.connections = connections;
        this.platforms = platforms;
    }

    /** What one run did, counted per channel and per post. */
    public record Report(int channels, int collected, int notSupported, int failed, int waiting, int points,
                         int snapshots) {
    }

    /** Mutable counters of one run; kept apart from the public record so the record stays a plain value. */
    private static final class Tally {
        int collected;
        int notSupported;
        int failed;
        int waiting;
        int points;
        int snapshots;
    }

    /** Runs one collection over every connected channel. */
    public Report collect() throws SQLException {
        Instant now = clock.instant();
        transactions.inTransaction(c -> {
            metrics.ensurePartitions(c, now);
            return null;
        });
        List<Channel> connected = transactions.inTransaction(c -> channels.listInState(c, ConnectionState.CONNECTED));
        Tally tally = new Tally();
        for (Channel channel : connected) {
            collectChannel(channel, now, tally);
        }
        int waiting = transactions.inTransaction(c -> channels.listInState(c, ConnectionState.NEEDS_RECONNECTION)).size();
        return new Report(connected.size(), tally.collected, tally.notSupported, tally.failed, tally.waiting + waiting,
                tally.points, tally.snapshots);
    }

    private void collectChannel(Channel channel, Instant now, Tally tally) throws SQLException {
        PlatformAdapter adapter = platforms.find(channel.platform()).orElse(null);
        if (adapter == null || !adapter.capabilities().metrics()) {
            tally.notSupported++;
            save(channel.id(), NOT_SUPPORTED_OUTCOME, null, null, NOT_SUPPORTED);
            return;
        }
        List<ExternalPost> live = new ArrayList<>();
        for (ExternalPost post : transactions.inTransaction(c -> posts.listForChannel(c, channel.id()))) {
            if (!post.removed()) {
                live.add(post);
            }
        }
        Map<UUID, Set<Long>> taken = transactions.inTransaction(c -> metrics.takenOffsetMinutes(c, channel.id()));
        Map<UUID, Instant> lastCollected = transactions.inTransaction(c -> metrics.lastCollectedAt(c, channel.id()));
        List<ExternalPost> due = new ArrayList<>();
        for (ExternalPost post : live) {
            if (isDue(post, now, taken.getOrDefault(post.id(), Set.of()), lastCollected.get(post.id()))) {
                due.add(post);
            }
        }
        if (due.isEmpty()) {
            // Nothing is due: the platform is not called, and the channel's last run succeeded.
            tally.collected++;
            save(channel.id(), COLLECTED, now, null, null);
            return;
        }
        String token;
        try {
            token = connections.freshGrant(channel.id()).accessToken();
        } catch (ProblemException refused) {
            if (refused.status() == 409) {
                tally.waiting++;
                return;
            }
            fail(channel.id(), REFUSED, now, tally);
            return;
        } catch (PlatformException failure) {
            fail(channel.id(), reasonOf(failure), now, tally);
            return;
        }
        Map<UUID, UUID> series = transactions.inTransaction(c -> metrics.seriesOfPosts(c, channel.id()));
        int batchSize = Math.max(1, adapter.metricsBatchSize());
        for (int from = 0; from < due.size(); from += batchSize) {
            List<ExternalPost> batch = due.subList(from, Math.min(due.size(), from + batchSize));
            List<String> ids = batch.stream().map(ExternalPost::platformItemId).toList();
            Map<String, Map<String, Long>> readings;
            try {
                readings = adapter.readMetrics(token, ids, Metrics.requested());
            } catch (PlatformAdapter.Unsupported unsupported) {
                tally.notSupported++;
                save(channel.id(), NOT_SUPPORTED_OUTCOME, null, null, NOT_SUPPORTED);
                return;
            } catch (PlatformException failure) {
                fail(channel.id(), reasonOf(failure), now, tally);
                return;
            }
            for (ExternalPost post : batch) {
                Map<String, Long> values = readings.get(post.platformItemId());
                if (values == null) {
                    // The platform returned nothing for this item: it is left unchanged, never written as zero.
                    continue;
                }
                storePost(post, values, series.get(post.id()), taken.getOrDefault(post.id(), Set.of()), now, tally);
            }
        }
        tally.collected++;
        save(channel.id(), COLLECTED, now, null, null);
    }

    /**
     * A post is read in a run only when a snapshot offset is reached that is not stored yet, or when its latest value is
     * older than {@link #STALE_AFTER}, or when it has never been read. Every other post is skipped by the run.
     */
    static boolean isDue(ExternalPost post, Instant now, Set<Long> takenMinutes, Instant lastCollected) {
        Duration age = Duration.between(post.publishedAt(), now);
        if (!Offsets.due(age, takenMinutes).isEmpty()) {
            return true;
        }
        return lastCollected == null || lastCollected.isBefore(now.minus(STALE_AFTER));
    }

    private void storePost(ExternalPost post, Map<String, Long> values, UUID seriesId, Set<Long> takenMinutes,
                           Instant now, Tally tally) throws SQLException {
        Duration age = Duration.between(post.publishedAt(), now);
        List<SnapshotValue> snapshots = new ArrayList<>();
        for (Duration offset : Offsets.due(age, takenMinutes)) {
            for (String metric : Metrics.ALL) {
                snapshots.add(new SnapshotValue(post.id(), (int) offset.toMinutes(), metric, values.get(metric), now,
                        (int) age.toMinutes()));
            }
        }
        Reading reading = new Reading(post, values, seriesId, now);
        transactions.inTransaction(c -> {
            metrics.storeReading(c, reading);
            metrics.storeSnapshots(c, snapshots);
            return null;
        });
        tally.points += values.size();
        tally.snapshots += snapshots.size();
    }

    private void fail(UUID channelId, String reason, Instant now, Tally tally) throws SQLException {
        tally.failed++;
        CollectionStatus previous = transactions.inTransaction(c -> metrics.findStatus(c, channelId)).orElse(null);
        Instant lastSuccess = previous == null ? null : previous.lastSuccessAt();
        save(channelId, FAILED, lastSuccess, now, reason);
    }

    private void save(UUID channelId, String outcome, Instant success, Instant failure, String reason)
            throws SQLException {
        CollectionStatus status = new CollectionStatus(channelId, outcome, success, failure, reason);
        transactions.inTransaction(c -> {
            metrics.saveStatus(c, status);
            return null;
        });
    }

    static String reasonOf(PlatformException failure) {
        return switch (failure.kind()) {
            case REJECTED -> REFUSED;
            case UNAVAILABLE -> UNREACHABLE;
            case RATE_LIMITED -> RATE_LIMITED;
        };
    }
}
