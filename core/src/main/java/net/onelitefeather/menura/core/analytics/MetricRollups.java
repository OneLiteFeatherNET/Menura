package net.onelitefeather.menura.core.analytics;

import net.onelitefeather.menura.core.analytics.Rollups.Input;
import net.onelitefeather.menura.core.db.Transactions;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The rollup job (change add-content-analytics): rebuilds the daily rows, the equal-age groups and the weekly rows of every
 * day a collection or an internal source marked as dirty. Each day is one transaction that locks its dirty mark first, so a
 * collection that writes the same day waits for the rebuild or is picked up by the next run. The dashboard reads only the
 * rows this service writes.
 */
public final class MetricRollups {

    private final Transactions transactions;
    private final MetricStore metrics;

    public MetricRollups(Transactions transactions, MetricStore metrics) {
        this.transactions = transactions;
        this.metrics = metrics;
    }

    /** What one run rebuilt: the dirty days it found, and the weeks whose weekly rows it wrote. */
    public record Report(int days, int weeks) {
    }

    /** Rebuilds every dirty day and each week that holds one of them. */
    public Report rebuild() throws SQLException {
        List<LocalDate> dirty = transactions.inTransaction(c -> metrics.dirtyDays(c));
        TreeSet<LocalDate> weeks = new TreeSet<>();
        for (LocalDate day : dirty) {
            boolean rebuilt = transactions.inTransaction(c -> rebuildDay(c, day));
            if (rebuilt) {
                weeks.add(weekStart(day));
            }
        }
        for (LocalDate week : weeks) {
            transactions.inTransaction(c -> {
                metrics.replaceWeek(c, week, Rollups.weekly(week, metrics.dailyRows(c, week, week.plusDays(6), null)));
                return null;
            });
        }
        return new Report(dirty.size(), weeks.size());
    }

    /** Rebuilds one day inside the caller's transaction; false when the day is not dirty any more. */
    boolean rebuildDay(Connection connection, LocalDate day) throws SQLException {
        if (!metrics.lockDirtyDay(connection, day)) {
            return false;
        }
        List<Input> inputs = new ArrayList<>(metrics.platformInputs(connection, day));
        inputs.addAll(metrics.internalInputs(connection, day));
        metrics.replaceDay(connection, day, Rollups.daily(day, inputs));
        metrics.replaceOffsets(connection, day, Rollups.offsets(day, metrics.snapshotInputs(connection, day)));
        metrics.clearDirtyDay(connection, day);
        return true;
    }

    /** The Monday that starts the week of the day. */
    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
