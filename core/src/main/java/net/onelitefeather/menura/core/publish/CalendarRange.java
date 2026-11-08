package net.onelitefeather.menura.core.publish;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * The time window of one calendar view, in the installation time zone: a month, a week (Monday to Sunday) or a list of
 * days from a date. {@code from} is inclusive and {@code to} exclusive, both in UTC.
 */
public record CalendarRange(View view, LocalDate firstDay, LocalDate lastDay, Instant from, Instant to, ZoneId zone) {

    public static final int LIST_DAYS_DEFAULT = 31;
    public static final int LIST_DAYS_MAX = 92;

    public enum View {
        MONTH("month"),
        WEEK("week"),
        LIST("list");

        private final String value;

        View(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Optional<View> parse(String name) {
            for (View view : values()) {
                if (view.value.equals(name)) {
                    return Optional.of(view);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The window of a view that contains {@code date}. A list starts at {@code date} and covers {@code days} days, which
     * must be between 1 and 92; the other views ignore {@code days}.
     */
    public static CalendarRange of(View view, LocalDate date, int days, ZoneId zone) {
        LocalDate first;
        LocalDate last;
        switch (view) {
            case MONTH -> {
                first = date.with(TemporalAdjusters.firstDayOfMonth());
                last = date.with(TemporalAdjusters.lastDayOfMonth());
            }
            case WEEK -> {
                first = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                last = first.plusDays(6);
            }
            default -> {
                if (days < 1 || days > LIST_DAYS_MAX) {
                    throw new IllegalArgumentException("a list covers between 1 and " + LIST_DAYS_MAX + " days");
                }
                first = date;
                last = date.plusDays(days - 1L);
            }
        }
        Instant from = first.atStartOfDay(zone).toInstant();
        Instant to = last.plusDays(1).atStartOfDay(zone).toInstant();
        return new CalendarRange(view, first, last, from, to, zone);
    }
}
