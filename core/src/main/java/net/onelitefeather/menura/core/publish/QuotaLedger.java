package net.onelitefeather.menura.core.publish;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Accounts the platform quota each channel uses per Pacific day (change add-youtube-integration). A step is charged before
 * the platform is called; a step that would exceed the remaining units, or a videos.insert that would exceed the daily
 * upload cap, is refused with a {@link Refusal} and the platform is not called. The caller runs the charge on the same
 * transaction as the attempt record, so a charge and its attempt are written together.
 */
public final class QuotaLedger {

    /** Units and upload calls used on one day by one channel. */
    public record Usage(int units, int uploads) {
    }

    /** What is left for one channel on the current day. */
    public record Remaining(int units, int uploads) {
    }

    /** The reason a step is refused. */
    public enum Reason { UNITS, UPLOAD_CAP }

    /** Stores the usage of a channel per day. Every method runs on the caller's connection. */
    public interface Store {
        Usage usage(Connection connection, UUID channelId, LocalDate day) throws SQLException;

        void save(Connection connection, UUID channelId, LocalDate day, Usage usage) throws SQLException;
    }

    /** A step refused before the platform is called; the post is shown as delayed until the quota resets. */
    public static final class Refusal extends RuntimeException {

        private final Reason reason;
        private final int remainingUnits;
        private final int cost;

        Refusal(Reason reason, int remainingUnits, int cost) {
            super(reason == Reason.UNITS
                    ? "The daily quota of the channel is used up: " + remainingUnits + " units are left and the step costs "
                    + cost + "."
                    : "The daily upload limit of the channel is used up.");
            this.reason = reason;
            this.remainingUnits = remainingUnits;
            this.cost = cost;
        }

        public Reason reason() {
            return reason;
        }

        public int remainingUnits() {
            return remainingUnits;
        }

        public int cost() {
            return cost;
        }
    }

    private final QuotaBudget budget;
    private final Store store;

    public QuotaLedger(QuotaBudget budget, Store store) {
        this.budget = Objects.requireNonNull(budget, "budget");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Charges one call of {@code method} to the channel's day of {@code now}, or refuses it. A refused call is not charged.
     */
    public void charge(Connection connection, UUID channelId, String method, Instant now) throws SQLException {
        LocalDate day = QuotaBudget.day(now);
        Usage used = store.usage(connection, channelId, day);
        int cost = budget.cost(method);
        if (used.units() + cost > budget.dailyUnits()) {
            throw new Refusal(Reason.UNITS, budget.dailyUnits() - used.units(), cost);
        }
        boolean upload = QuotaBudget.VIDEOS_INSERT.equals(method);
        if (upload && used.uploads() + 1 > budget.dailyUploads()) {
            throw new Refusal(Reason.UPLOAD_CAP, budget.dailyUnits() - used.units(), cost);
        }
        store.save(connection, channelId, day, new Usage(used.units() + cost, used.uploads() + (upload ? 1 : 0)));
    }

    /** The daily budget the ledger accounts against. */
    public QuotaBudget budget() {
        return budget;
    }

    /** What is left for the channel on the day of {@code now}. */
    public Remaining remaining(Connection connection, UUID channelId, Instant now) throws SQLException {
        Usage used = store.usage(connection, channelId, QuotaBudget.day(now));
        return new Remaining(budget.dailyUnits() - used.units(), budget.dailyUploads() - used.uploads());
    }
}
