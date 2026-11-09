package net.onelitefeather.menura.core.attribution.support;

import net.onelitefeather.menura.core.attribution.AttributablePost;
import net.onelitefeather.menura.core.attribution.FirstJoin;
import net.onelitefeather.menura.core.attribution.JoinAnswer;
import net.onelitefeather.menura.core.attribution.JoinEvent;
import net.onelitefeather.menura.core.attribution.JoinSettings;
import net.onelitefeather.menura.core.attribution.JoinStore;
import net.onelitefeather.menura.core.attribution.StatedSource;

import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the join tables in maps. Like the tables, it refuses a second event or answer with the same key by returning false,
 * and it keeps the posts the attribution reads. The connection argument is ignored.
 */
public final class InMemoryJoinStore implements JoinStore {

    private JoinSettings settings;
    private final Map<UUID, JoinEvent> events = new HashMap<>();
    private final Map<UUID, JoinAnswer> answers = new HashMap<>();
    private final List<AttributablePost> posts = new ArrayList<>();
    private final Map<LocalDate, Map<String, Long>> counts = new HashMap<>();

    /** Adds a post the attribution reads, with the visibility of its series. */
    public void addPost(AttributablePost post) {
        posts.add(post);
    }

    /** The number of stored events, for tests that check a rejected payload stored nothing. */
    public int eventCount() {
        return events.size();
    }

    public int answerCount() {
        return answers.size();
    }

    /** The stored counter of one day and source, or zero. */
    public long count(LocalDate day, String source) {
        return counts.getOrDefault(day, Map.of()).getOrDefault(source, 0L);
    }

    @Override
    public Optional<JoinSettings> findSettings(Connection connection) {
        return Optional.ofNullable(settings);
    }

    @Override
    public void saveSettings(Connection connection, JoinSettings settings) {
        this.settings = settings;
    }

    @Override
    public Optional<JoinEvent> findEvent(Connection connection, UUID eventId) {
        return Optional.ofNullable(events.get(eventId));
    }

    @Override
    public boolean insertEvent(Connection connection, JoinEvent event) {
        return events.putIfAbsent(event.eventId(), event) == null;
    }

    @Override
    public Optional<JoinAnswer> findAnswer(Connection connection, UUID eventId) {
        return Optional.ofNullable(answers.get(eventId));
    }

    @Override
    public boolean insertAnswer(Connection connection, JoinAnswer answer) {
        return answers.putIfAbsent(answer.eventId(), answer) == null;
    }

    @Override
    public List<FirstJoin> firstJoinsBetween(Connection connection, Instant from, Instant to) {
        return events.values().stream()
                .filter(JoinEvent::firstJoin)
                .filter(event -> !event.joinedAt().isBefore(from) && !event.joinedAt().isAfter(to))
                .sorted(Comparator.comparing(JoinEvent::joinedAt).thenComparing(JoinEvent::eventId))
                .map(event -> new FirstJoin(event.eventId(), event.joinedAt(),
                        Optional.ofNullable(answers.get(event.eventId())).map(JoinAnswer::source).orElse(null)))
                .toList();
    }

    @Override
    public List<AttributablePost> postsPublishedBetween(Connection connection, Instant from, Instant to) {
        return posts.stream()
                .filter(post -> !post.publishedAt().isBefore(from) && !post.publishedAt().isAfter(to))
                .sorted(Comparator.comparing(AttributablePost::publishedAt).thenComparing(AttributablePost::postId))
                .toList();
    }

    @Override
    public Optional<AttributablePost> findPost(Connection connection, UUID postId) {
        return posts.stream().filter(post -> post.postId().equals(postId)).findFirst();
    }

    @Override
    public void addToDay(Connection connection, LocalDate day, String source, long delta) {
        counts.computeIfAbsent(day, key -> new HashMap<>()).merge(source, delta, Long::sum);
    }

    @Override
    public void replaceDay(Connection connection, LocalDate day, Map<String, Long> values) {
        counts.put(day, new HashMap<>(values));
    }

    @Override
    public List<DayCount> countsBetween(Connection connection, LocalDate from, LocalDate to) {
        List<DayCount> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, Long>> day : new java.util.TreeMap<>(counts).entrySet()) {
            if (day.getKey().isBefore(from) || day.getKey().isAfter(to)) {
                continue;
            }
            for (Map.Entry<String, Long> counter : new java.util.TreeMap<>(day.getValue()).entrySet()) {
                result.add(new DayCount(day.getKey(), counter.getKey(), counter.getValue()));
            }
        }
        return result;
    }

    /** The stated source of a stored answer, for tests. */
    public Optional<StatedSource> answerOf(UUID eventId) {
        return Optional.ofNullable(answers.get(eventId)).map(JoinAnswer::source);
    }
}
