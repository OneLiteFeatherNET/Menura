package net.onelitefeather.menura.core.publish;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Quota accounting per channel and per Pacific day, with the refusal before the platform is called. No database. */
class QuotaLedgerTest {

    private static final UUID CHANNEL = UUID.fromString("c0a00000-0000-4000-8000-000000000001");
    /** Sunday 2026-11-08 at 12:00 in Pacific time (UTC-8 after the November change). */
    private static final Instant NOON_PACIFIC = Instant.parse("2026-11-08T20:00:00Z");

    @Test
    void aStepChargesTheConfiguredUnitsOfTheMethod() throws Exception {
        QuotaLedger ledger = new QuotaLedger(QuotaBudget.youtubeDefaults(), new InMemoryUsage());

        ledger.charge(null, CHANNEL, QuotaBudget.PLAYLIST_ITEMS_INSERT, NOON_PACIFIC);

        assertEquals(9950, ledger.remaining(null, CHANNEL, NOON_PACIFIC).units(),
                "a playlist insert of 50 units leaves 9950 of the 10000 daily units");
    }

    @Test
    void aStepThatWouldExceedTheRemainingUnitsIsRefusedWithTheRemainingAndTheCost() throws Exception {
        QuotaLedger ledger = new QuotaLedger(new QuotaBudget(90, 100, QuotaBudget.youtubeDefaults().costs()),
                new InMemoryUsage());
        ledger.charge(null, CHANNEL, QuotaBudget.PLAYLIST_ITEMS_INSERT, NOON_PACIFIC);

        QuotaLedger.Refusal refusal = assertThrows(QuotaLedger.Refusal.class,
                () -> ledger.charge(null, CHANNEL, QuotaBudget.PLAYLIST_ITEMS_INSERT, NOON_PACIFIC),
                "a second playlist insert of 50 units does not fit into the 40 remaining units");

        assertEquals(40, refusal.remainingUnits(), "the refusal names the remaining units");
        assertEquals(50, refusal.cost(), "the refusal names the cost of the step");
        assertEquals(QuotaLedger.Reason.UNITS, refusal.reason(), "the refusal names the unit quota as the reason");
        assertEquals(40, ledger.remaining(null, CHANNEL, NOON_PACIFIC).units(), "a refused step is not charged");
    }

    @Test
    void anUploadIsRefusedWhenTheDailyUploadCapIsUsedUpEvenWithUnitsLeft() throws Exception {
        QuotaLedger ledger = new QuotaLedger(new QuotaBudget(10_000, 1, QuotaBudget.youtubeDefaults().costs()),
                new InMemoryUsage());
        ledger.charge(null, CHANNEL, QuotaBudget.VIDEOS_INSERT, NOON_PACIFIC);

        QuotaLedger.Refusal refusal = assertThrows(QuotaLedger.Refusal.class,
                () -> ledger.charge(null, CHANNEL, QuotaBudget.VIDEOS_INSERT, NOON_PACIFIC),
                "the second upload exceeds the daily upload cap of one");

        assertEquals(QuotaLedger.Reason.UPLOAD_CAP, refusal.reason(), "the refusal names the upload cap");
        assertEquals(0, ledger.remaining(null, CHANNEL, NOON_PACIFIC).uploads(), "no upload is left for the day");
        assertEquals(9900, ledger.remaining(null, CHANNEL, NOON_PACIFIC).units(),
                "the refused upload did not consume units either");
    }

    @Test
    void theQuotaOfAChannelResetsAtMidnightPacificTimeNotAtMidnightUtc() throws Exception {
        QuotaLedger ledger = new QuotaLedger(new QuotaBudget(100, 100, QuotaBudget.youtubeDefaults().costs()),
                new InMemoryUsage());
        Instant justBeforePacificMidnight = Instant.parse("2026-11-09T07:59:59Z");
        Instant pacificMidnight = Instant.parse("2026-11-09T08:00:00Z");
        ledger.charge(null, CHANNEL, QuotaBudget.VIDEOS_LIST, justBeforePacificMidnight);

        assertEquals(99, ledger.remaining(null, CHANNEL, justBeforePacificMidnight).units(),
                "one second before Pacific midnight the day still counts the call");
        assertEquals(100, ledger.remaining(null, CHANNEL, pacificMidnight).units(),
                "at Pacific midnight the quota of the new day is full again");
    }

    @Test
    void theQuotaDayIsThePacificDate() {
        assertEquals(LocalDate.parse("2026-11-08"), QuotaBudget.day(Instant.parse("2026-11-09T07:00:00Z")),
                "07:00 UTC is still the previous day in Pacific time");
        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), QuotaBudget.nextReset(Instant.parse("2026-11-08T20:00:00Z")),
                "the next reset is the next Pacific midnight");
    }

    @Test
    void theDefaultCostsMatchTheDocumentedMethodCosts() {
        Map<String, Integer> costs = QuotaBudget.youtubeDefaults().costs();

        assertEquals(50, costs.get(QuotaBudget.PLAYLIST_ITEMS_INSERT), "playlistItems.insert costs 50 units");
        assertEquals(50, costs.get(QuotaBudget.THUMBNAILS_SET), "thumbnails.set costs 50 units");
        assertEquals(1, costs.get(QuotaBudget.VIDEOS_LIST), "videos.list costs 1 unit");
        assertEquals(100, costs.get(QuotaBudget.VIDEOS_INSERT), "videos.insert is configured at the 100-unit table value");
    }

    /** The usage of channels per day, held in memory. */
    static final class InMemoryUsage implements QuotaLedger.Store {
        private final Map<String, QuotaLedger.Usage> usage = new HashMap<>();

        @Override
        public QuotaLedger.Usage usage(Connection connection, UUID channelId, LocalDate day) {
            return usage.getOrDefault(channelId + "/" + day, new QuotaLedger.Usage(0, 0));
        }

        @Override
        public void save(Connection connection, UUID channelId, LocalDate day, QuotaLedger.Usage value) {
            usage.put(channelId + "/" + day, value);
        }
    }
}
