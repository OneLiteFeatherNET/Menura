package net.onelitefeather.menura.core.publish;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * The daily quota of one platform project and the unit cost of each method (change add-youtube-integration). The costs are
 * configuration, not code: a change in the platform's numbers changes this table only.
 *
 * <p>YouTube quota (checked 2026-11-08 at https://developers.google.com/youtube/v3/determine_quota_cost): 10,000 units per
 * day for the shared pool, reset at midnight Pacific time; the table lists videos.insert at 100 units with a daily cap of
 * 100 calls, playlistItems.insert and thumbnails.set at 50 units, videos.list at 1 unit. The videos.insert row contradicts
 * itself on that page ("Each call costs 1 quota" in a separate bucket), so the 100-unit value is charged against the pool
 * and counted against the cap; the Cloud Console value is confirmed before the check is trusted (tasks 1.1).
 *
 * @param dailyUnits   units the project may use per Pacific day across methods
 * @param dailyUploads videos.insert calls per Pacific day
 * @param costs        units per method name
 */
public record QuotaBudget(int dailyUnits, int dailyUploads, Map<String, Integer> costs) {

    public static final String VIDEOS_INSERT = "videos.insert";
    public static final String PLAYLIST_ITEMS_INSERT = "playlistItems.insert";
    public static final String THUMBNAILS_SET = "thumbnails.set";
    public static final String VIDEOS_LIST = "videos.list";

    /** The zone in which the platform's daily quota resets. */
    public static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    public QuotaBudget {
        costs = Map.copyOf(costs);
        if (dailyUnits < 1 || dailyUploads < 1) {
            throw new IllegalArgumentException("the daily quota must be positive");
        }
    }

    /** The documented YouTube defaults described in the class comment. */
    public static QuotaBudget youtubeDefaults() {
        return new QuotaBudget(10_000, 100, Map.of(VIDEOS_INSERT, 100, PLAYLIST_ITEMS_INSERT, 50, THUMBNAILS_SET, 50,
                VIDEOS_LIST, 1));
    }

    /** The units of one call of the method. A method without a configured cost is refused by the ledger. */
    public int cost(String method) {
        Integer cost = costs.get(method);
        if (cost == null) {
            throw new IllegalArgumentException("no quota cost is configured for " + method);
        }
        return cost;
    }

    /** The quota day of an instant: its date in Pacific time. */
    public static LocalDate day(Instant now) {
        return now.atZone(PACIFIC).toLocalDate();
    }

    /** The instant at which the quota of the day of {@code now} resets: the next Pacific midnight. */
    public static Instant nextReset(Instant now) {
        return day(now).plusDays(1).atStartOfDay(PACIFIC).toInstant();
    }
}
