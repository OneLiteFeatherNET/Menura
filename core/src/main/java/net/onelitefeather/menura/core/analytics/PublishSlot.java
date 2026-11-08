package net.onelitefeather.menura.core.analytics;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * The publish hour, weekday and week of a post in the installation time zone (change add-content-analytics). Europe/Berlin
 * is the zone the series coverage uses, so an hour or a week means the same to the team as in the coverage report.
 */
public final class PublishSlot {

    /** The time zone of the team, as the series coverage uses it. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private PublishSlot() {
    }

    /** The hour of the day, 0 to 23, in the zone. */
    public static int hour(Instant publishedAt) {
        return publishedAt.atZone(ZONE).getHour();
    }

    /** The ISO weekday in the zone, 1 (Monday) to 7 (Sunday). */
    public static int weekday(Instant publishedAt) {
        return publishedAt.atZone(ZONE).getDayOfWeek().getValue();
    }

    /** The Monday that starts the week of the instant, in the zone. */
    public static LocalDate weekStart(Instant publishedAt) {
        ZonedDateTime local = publishedAt.atZone(ZONE);
        return local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The ISO week as {@code 2026-W45}, in the zone. */
    public static String isoWeek(Instant publishedAt) {
        ZonedDateTime local = publishedAt.atZone(ZONE);
        return String.format("%04d-W%02d", local.get(IsoFields.WEEK_BASED_YEAR),
                local.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** The instant at which a local date starts in the zone, for the bounds of a period. */
    public static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }
}
