package net.onelitefeather.menura.core.series;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of series, their assignments and episode numbers. Every method runs on the connection of the transaction
 * the caller opened.
 */
public interface SeriesStore {

    /** Every series, in name order, active and inactive. */
    List<ContentSeries> list(Connection connection) throws SQLException;

    Optional<ContentSeries> find(Connection connection, UUID id) throws SQLException;

    void insert(Connection connection, ContentSeries series) throws SQLException;

    /** Replaces a series in place, badge texts and playlists included. Fails when the series does not exist. */
    void update(Connection connection, ContentSeries series) throws SQLException;

    /** The assignment of a post, locked until the transaction ends so that two assignments of one post run one after the other. */
    Optional<SeriesAssignment> lockAssignment(Connection connection, UUID postId) throws SQLException;

    Optional<SeriesAssignment> findAssignment(Connection connection, UUID postId) throws SQLException;

    /** Every assignment, for the coverage report and the playlist check. */
    List<SeriesAssignment> listAssignments(Connection connection) throws SQLException;

    /** Writes the decision of a post, replacing the one it had. */
    void saveAssignment(Connection connection, SeriesAssignment assignment) throws SQLException;

    /** The episode number a post has in a series, if it ever received one there. */
    Optional<Integer> episodeOf(Connection connection, UUID seriesId, UUID postId) throws SQLException;

    /**
     * Takes the next episode number of a series. The counter only increases, so a number is never given twice, not even
     * after its post leaves the series.
     */
    int nextEpisodeNumber(Connection connection, UUID seriesId) throws SQLException;

    void saveEpisode(Connection connection, UUID seriesId, UUID postId, int number) throws SQLException;

    /** The episodes of a series in number order. */
    List<Episode> episodes(Connection connection, UUID seriesId) throws SQLException;

    /** A post's number within one series. */
    record Episode(int number, UUID postId) {
    }
}
