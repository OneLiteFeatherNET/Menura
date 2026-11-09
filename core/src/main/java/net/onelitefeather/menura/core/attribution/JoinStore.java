package net.onelitefeather.menura.core.attribution;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of the join settings, the join events, their source answers, the external posts the attribution reads and
 * the daily join rollup (change add-join-attribution). Every method runs on the connection of the transaction the caller
 * opened.
 */
public interface JoinStore {

    Optional<JoinSettings> findSettings(Connection connection) throws SQLException;

    void saveSettings(Connection connection, JoinSettings settings) throws SQLException;

    Optional<JoinEvent> findEvent(Connection connection, UUID eventId) throws SQLException;

    /** Inserts the event unless its id is stored already; returns false in that case and writes nothing. */
    boolean insertEvent(Connection connection, JoinEvent event) throws SQLException;

    Optional<JoinAnswer> findAnswer(Connection connection, UUID eventId) throws SQLException;

    /** Inserts the answer unless the join has one already; returns false in that case and writes nothing. */
    boolean insertAnswer(Connection connection, JoinAnswer answer) throws SQLException;

    /** The first joins from {@code from} to {@code to}, both inclusive, in time order, with the stated source if any. */
    List<FirstJoin> firstJoinsBetween(Connection connection, Instant from, Instant to) throws SQLException;

    /** The external posts published from {@code from} to {@code to}, both inclusive, with the visibility of their series. */
    List<AttributablePost> postsPublishedBetween(Connection connection, Instant from, Instant to) throws SQLException;

    /** One external post with the visibility of its series, if the post exists. */
    Optional<AttributablePost> findPost(Connection connection, UUID postId) throws SQLException;

    /** Adds to one counter of the daily rollup, creating it when it does not exist yet. */
    void addToDay(Connection connection, LocalDate day, String source, long delta) throws SQLException;

    /** Replaces every counter of one day in the rollup with the given values. */
    void replaceDay(Connection connection, LocalDate day, Map<String, Long> counts) throws SQLException;

    /** The rollup counters of the days from {@code from} to {@code to}, both inclusive. */
    List<DayCount> countsBetween(Connection connection, LocalDate from, LocalDate to) throws SQLException;

    /** One counter of the daily rollup: a day, a source ('total' or a stated source) and the count. */
    record DayCount(LocalDate day, String source, long joins) {
    }
}
