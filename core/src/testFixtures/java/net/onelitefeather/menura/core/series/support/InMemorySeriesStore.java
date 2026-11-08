package net.onelitefeather.menura.core.series.support;

import net.onelitefeather.menura.core.series.ContentSeries;
import net.onelitefeather.menura.core.series.SeriesAssignment;
import net.onelitefeather.menura.core.series.SeriesStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps series, assignments and episode numbers in maps. Like the tables, it refuses a second series with the same
 * hashtag, a second episode number of a series and an update of a missing series. The connection argument is ignored.
 */
public final class InMemorySeriesStore implements SeriesStore {

    private final Map<UUID, ContentSeries> series = new HashMap<>();
    private final Map<UUID, SeriesAssignment> assignments = new HashMap<>();
    private final Map<String, Integer> episodes = new HashMap<>();
    private final Map<UUID, Integer> counters = new HashMap<>();
    private int writes;

    @Override
    public List<ContentSeries> list(Connection connection) {
        return series.values().stream().sorted(Comparator.comparing(ContentSeries::name).thenComparing(ContentSeries::id))
                .toList();
    }

    @Override
    public Optional<ContentSeries> find(Connection connection, UUID id) {
        return Optional.ofNullable(series.get(id));
    }

    @Override
    public void insert(Connection connection, ContentSeries value) throws SQLException {
        boolean taken = series.values().stream().anyMatch(other -> other.hashtag().equals(value.hashtag()));
        if (taken || series.containsKey(value.id())) {
            throw new SQLException("duplicate series for the hashtag or the identifier");
        }
        series.put(value.id(), value);
        writes++;
    }

    @Override
    public void update(Connection connection, ContentSeries value) throws SQLException {
        if (!series.containsKey(value.id())) {
            throw new SQLException("the series to update does not exist");
        }
        series.put(value.id(), value);
        writes++;
    }

    @Override
    public Optional<SeriesAssignment> lockAssignment(Connection connection, UUID postId) {
        return findAssignment(connection, postId);
    }

    @Override
    public Optional<SeriesAssignment> findAssignment(Connection connection, UUID postId) {
        return Optional.ofNullable(assignments.get(postId));
    }

    @Override
    public List<SeriesAssignment> listAssignments(Connection connection) {
        return List.copyOf(assignments.values());
    }

    @Override
    public void saveAssignment(Connection connection, SeriesAssignment assignment) {
        assignments.put(assignment.postId(), assignment);
        writes++;
    }

    @Override
    public Optional<Integer> episodeOf(Connection connection, UUID seriesId, UUID postId) {
        return Optional.ofNullable(episodes.get(key(seriesId, postId)));
    }

    @Override
    public int nextEpisodeNumber(Connection connection, UUID seriesId) {
        int next = counters.getOrDefault(seriesId, 0) + 1;
        counters.put(seriesId, next);
        return next;
    }

    @Override
    public void saveEpisode(Connection connection, UUID seriesId, UUID postId, int number) throws SQLException {
        boolean numberTaken = episodes.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(seriesId + "/") && entry.getValue() == number);
        if (numberTaken) {
            throw new SQLException("the episode number is already taken in this series");
        }
        episodes.put(key(seriesId, postId), number);
        writes++;
    }

    @Override
    public List<Episode> episodes(Connection connection, UUID seriesId) {
        return episodes.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(seriesId + "/"))
                .map(entry -> new Episode(entry.getValue(), UUID.fromString(entry.getKey().substring(37))))
                .sorted(Comparator.comparingInt(Episode::number))
                .toList();
    }

    /** Series, assignment and episode rows written so far. A refused request adds none. */
    public int writes() {
        return writes;
    }

    /** Removes every row and resets the counters, for a test that shares the fake with the server. */
    public void clear() {
        series.clear();
        assignments.clear();
        episodes.clear();
        counters.clear();
        writes = 0;
    }

    private static String key(UUID seriesId, UUID postId) {
        return seriesId + "/" + postId;
    }
}
