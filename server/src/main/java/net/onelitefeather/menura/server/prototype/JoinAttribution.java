package net.onelitefeather.menura.server.prototype;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Join attribution over one shared join domain. Measured: the first joins per ISO week and the answers to the
 * in-game question "Wo hast du uns gefunden?" (asked once, on the first join). Estimated: each first join is split
 * evenly across the posts published in the 72 hours before it. A join that named a platform is split only across
 * that platform's posts; a join that named friends, other or nothing is split across all posts. A platform join
 * with no post of that platform in its window gives no post an estimate. Repeat joins are ignored.
 */
final class JoinAttribution {

    static final Duration WINDOW = Duration.ofHours(72);

    /** The sources in the order the weekly counts list them. The last one is "unanswered". */
    static final List<String> SOURCES = List.of(
            "youtube", "tiktok", "instagram", "twitch", "friends", "other", "unanswered");

    private static final List<String> PLATFORMS = List.of("youtube", "tiktok", "instagram", "twitch");
    private static final String UNANSWERED = "unanswered";

    private JoinAttribution() {
    }

    /**
     * The source of one answer. A missing or blank answer is unanswered; a known source is itself; any other text is
     * "other", so a stray value never creates a source the weekly counts do not list.
     */
    static String sourceOf(String answer) {
        if (answer == null || answer.isBlank()) {
            return UNANSWERED;
        }
        String normalised = answer.trim().toLowerCase(Locale.ROOT);
        return SOURCES.contains(normalised) ? normalised : "other";
    }

    /** The estimated first joins per post id. Only posts that received a share appear. */
    static Map<String, Double> estimates(List<Model.Post> posts, List<Model.JoinEvent> joins) {
        Map<String, Double> shares = new HashMap<>();
        for (Model.JoinEvent join : joins) {
            if (!join.firstJoin()) {
                continue;
            }
            String source = sourceOf(join.answer());
            Instant from = join.at().minus(WINDOW);
            List<Model.Post> eligible = posts.stream()
                    .filter(post -> !PLATFORMS.contains(source) || post.platform().equals(source))
                    .filter(post -> {
                        Instant published = Instant.parse(post.publishedAt());
                        return !published.isBefore(from) && !published.isAfter(join.at());
                    })
                    .toList();
            if (eligible.isEmpty()) {
                continue;
            }
            double share = 1.0 / eligible.size();
            for (Model.Post post : eligible) {
                shares.merge(post.id(), share, Double::sum);
            }
        }
        return shares;
    }

    /** Measured first joins per ISO week and source. Weeks without a first join are not listed. */
    static List<Model.WeeklyAttribution> weekly(List<Model.JoinEvent> joins) {
        TreeMap<String, Map<String, Integer>> counts = new TreeMap<>();
        for (Model.JoinEvent join : joins) {
            if (!join.firstJoin()) {
                continue;
            }
            counts.computeIfAbsent(IsoWeek.of(join.at()), week -> new HashMap<>())
                    .merge(sourceOf(join.answer()), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> row(entry.getKey(), entry.getValue())).toList();
    }

    private static Model.WeeklyAttribution row(String week, Map<String, Integer> bySource) {
        int firstJoins = bySource.values().stream().mapToInt(Integer::intValue).sum();
        int unanswered = bySource.getOrDefault(UNANSWERED, 0);
        int answered = firstJoins - unanswered;
        double rate = firstJoins == 0 ? 0.0 : Rounding.to((double) answered / firstJoins, 2);
        List<Model.SourceCount> counts = SOURCES.stream()
                .map(source -> new Model.SourceCount(source, bySource.getOrDefault(source, 0)))
                .toList();
        return new Model.WeeklyAttribution(week, firstJoins, answered, rate, counts);
    }
}
