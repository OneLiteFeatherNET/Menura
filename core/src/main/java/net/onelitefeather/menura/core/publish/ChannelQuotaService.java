package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The remaining daily quota of a YouTube channel (change add-youtube-integration, the quota of the project and the channel's
 * own use of it). The figures are read from the same ledger the publication charges, so the API shows what the job will see.
 */
public final class ChannelQuotaService {

    /**
     * The quota of one channel on the current Pacific day.
     *
     * @param unitsPerDay      the units of the day in the project's pool
     * @param unitsRemaining   the units not yet used on the day
     * @param uploadsPerDay    the videos.insert calls of the day
     * @param uploadsRemaining the uploads not yet used on the day
     * @param resetsAt         the next Pacific midnight, when the figures start over
     */
    public record QuotaView(int unitsPerDay, int unitsRemaining, int uploadsPerDay, int uploadsRemaining, Instant resetsAt) {
    }

    private final Transactions transactions;
    private final Clock clock;
    private final ChannelStore channels;
    private final QuotaLedger ledger;

    public ChannelQuotaService(Transactions transactions, Clock clock, ChannelStore channels, QuotaLedger ledger) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /** The remaining quota of a YouTube channel. Another platform has no such quota in this build and is not found. */
    public QuotaView quota(UUID channelId) throws SQLException {
        return transactions.inTransaction(c -> {
            Channel channel = channels.findChannel(c, channelId).orElseThrow(() ->
                    new ProblemException(404, "Not Found", "The channel does not exist."));
            if (!YouTubeAdapter.PLATFORM.equals(channel.platform())) {
                throw new ProblemException(404, "Not Found", "Only a YouTube channel has a daily quota in this build.");
            }
            return read(c, channelId);
        });
    }

    private QuotaView read(Connection connection, UUID channelId) throws SQLException {
        Instant now = clock.instant();
        QuotaLedger.Remaining remaining = ledger.remaining(connection, channelId, now);
        QuotaBudget budget = ledger.budget();
        return new QuotaView(budget.dailyUnits(), remaining.units(), budget.dailyUploads(), remaining.uploads(),
                QuotaBudget.nextReset(now));
    }
}
