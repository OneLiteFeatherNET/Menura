package net.onelitefeather.menura.core.analytics;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * The publish hour, weekday and week of a post in the installation time zone (change add-content-analytics). The zone is
 * configured with MENURA_ANALYTICS_TIME_ZONE and defaults to Europe/Berlin, the zone the series coverage uses.
 */
public final class PublishSlot {

    private final ZoneId zone;

    public PublishSlot(ZoneId zone) {
        this.zone = zone;
    }

    /** The time zone the hours, weekdays and weeks are taken in. */
    public ZoneId zone() {
        return zone;
    }

    /** The hour of the day, 0 to 23, in the zone. */
    public int hour(Instant publishedAt) {
        return publishedAt.atZone(zone).getHour();
    }

    /** The ISO weekday in the zone, 1 (Monday) to 7 (Sunday). */
    public int weekday(Instant publishedAt) {
        return publishedAt.atZone(zone).getDayOfWeek().getValue();
    }

    /** The Monday that starts the week of the instant, in the zone. */
    public LocalDate weekStart(Instant publishedAt) {
        ZonedDateTime local = publishedAt.atZone(zone);
        return local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The ISO week as {@code 2026-W45}, in the zone. */
    public String isoWeek(Instant publishedAt) {
        ZonedDateTime local = publishedAt.atZone(zone);
        return String.format("%04d-W%02d", local.get(IsoFields.WEEK_BASED_YEAR),
                local.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** The instant at which a local date starts in the zone, for the bounds of a period. */
    public Instant startOf(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }
}
