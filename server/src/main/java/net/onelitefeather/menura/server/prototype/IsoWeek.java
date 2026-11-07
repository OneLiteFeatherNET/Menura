package net.onelitefeather.menura.server.prototype;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Locale;
import java.util.regex.Pattern;

/** ISO-8601 weeks in UTC, written as {@code 2026-W45}. The week-based year is used, so 2027-01-01 may be W53 of 2026. */
final class IsoWeek {

    private static final Pattern FORMAT = Pattern.compile("^\\d{4}-W(0[1-9]|[1-4]\\d|5[0-3])$");

    private IsoWeek() {
    }

    static String of(Instant at) {
        LocalDate day = at.atZone(ZoneOffset.UTC).toLocalDate();
        return String.format(Locale.ROOT, "%04d-W%02d", day.get(IsoFields.WEEK_BASED_YEAR),
                day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    static boolean isWellFormed(String week) {
        return week != null && FORMAT.matcher(week).matches();
    }
}
