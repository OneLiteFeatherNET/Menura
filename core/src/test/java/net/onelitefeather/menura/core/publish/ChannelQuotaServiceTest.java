package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.support.DirectTransactions;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.channel.support.MutableClock;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.publish.ChannelQuotaService.QuotaView;
import net.onelitefeather.menura.core.publish.support.InMemoryQuotaUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The remaining daily quota of a channel as the API shows it. Fixed clock, in-memory stores, no network. */
class ChannelQuotaServiceTest {

    private static final Instant NOON = Instant.parse("2026-11-08T20:00:00Z");
    private static final UUID YOUTUBE = UUID.fromString("c0a00000-0000-4000-8000-0000000000a1");
    private static final UUID TWITCH = UUID.fromString("c0a00000-0000-4000-8000-0000000000a2");

    @Test
    void aFreshYouTubeChannelHasTheWholeDailyQuotaLeft() throws Exception {
        Fixture world = new Fixture();

        QuotaView quota = world.service.quota(YOUTUBE);

        assertEquals(10_000, quota.unitsRemaining(), "no unit is used on a new day");
        assertEquals(100, quota.uploadsRemaining(), "no upload is used on a new day");
        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), quota.resetsAt(), "the day resets at the next Pacific midnight");
    }

    @Test
    void theChargedCallsOfTheDayAreSubtractedFromTheRemainingQuota() throws Exception {
        Fixture world = new Fixture();
        world.ledger.charge(null, YOUTUBE, QuotaBudget.VIDEOS_INSERT, NOON);
        world.ledger.charge(null, YOUTUBE, QuotaBudget.PLAYLIST_ITEMS_INSERT, NOON);

        QuotaView quota = world.service.quota(YOUTUBE);

        assertEquals(9_850, quota.unitsRemaining(), "an upload of 100 units and a playlist insert of 50 units are used");
        assertEquals(99, quota.uploadsRemaining(), "one upload of the cap of 100 is used");
        assertEquals(10_000, quota.unitsPerDay(), "the daily units are shown");
        assertEquals(100, quota.uploadsPerDay(), "the daily upload cap is shown");
    }

    @Test
    void aChannelOfAnotherPlatformHasNoYouTubeQuota() throws Exception {
        Fixture world = new Fixture();

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.quota(TWITCH),
                "only a YouTube channel has a daily quota in this build");

        assertEquals(404, refused.status(), "the quota of another platform is not found");
    }

    /** The service over in-memory stores and a fixed clock. */
    private static final class Fixture {
        final InMemoryChannelStore channels = new InMemoryChannelStore();
        final InMemoryQuotaUsage usage = new InMemoryQuotaUsage();
        final QuotaLedger ledger = new QuotaLedger(QuotaBudget.youtubeDefaults(), usage);
        final ChannelQuotaService service;

        Fixture() {
            channels.insertChannel(null, new Channel(YOUTUBE, "youtube", "UC-quota", "Quota", ConnectionState.CONNECTED, null,
                    Set.of(), Set.of(), null, NOON, NOON, null));
            channels.insertChannel(null, new Channel(TWITCH, "twitch", "quota-twitch", "Twitch", ConnectionState.CONNECTED,
                    null, Set.of(), Set.of(), null, NOON, NOON, null));
            service = new ChannelQuotaService(new DirectTransactions(), new MutableClock(NOON), channels, ledger);
        }
    }
}
