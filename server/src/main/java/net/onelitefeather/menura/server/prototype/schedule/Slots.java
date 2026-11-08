package net.onelitefeather.menura.server.prototype.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The weekly series slots in Europe/Berlin. A slot keeps its wall-clock time across the DST changes: 18:00 on a
 * Tuesday is 17:00 UTC in winter and 16:00 UTC in summer, and {@link ZonedDateTime} does that conversion.
 */
final class Slots {

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** A search horizon of about 5000 weeks, so a weekday and time that never occur fail instead of looping. */
    private static final int MAX_DAYS = 7 * 5_000;

    private Slots() {
    }

    /**
     * The next {@code count} instants at which {@code weekday} and {@code slot} (Berlin wall time) occur, not before
     * {@code earliest}, skipping every instant for which {@code taken} is true. Oldest first.
     */
    static List<Instant> next(DayOfWeek weekday, LocalTime slot, Instant earliest, int count,
                              Predicate<Instant> taken) {
        List<Instant> found = new ArrayList<>();
        LocalDate day = earliest.atZone(BERLIN).toLocalDate().minusDays(1);
        for (int i = 0; i < MAX_DAYS && found.size() < count; i++, day = day.plusDays(1)) {
            if (day.getDayOfWeek() != weekday) {
                continue;
            }
            Instant at = at(day, slot);
            if (!at.isBefore(earliest) && !taken.test(at)) {
                found.add(at);
            }
        }
        if (found.size() < count) {
            throw new IllegalStateException("no free series slot within the search horizon");
        }
        return found;
    }

    /** The instant of a Berlin wall-clock time on a date. */
    static Instant at(LocalDate day, LocalTime slot) {
        return ZonedDateTime.of(day, slot, BERLIN).toInstant();
    }
}
