package net.onelitefeather.menura.server.prototype;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Share of a channel's shorts in a week that belong to a series. A short is covered when it has a series or is
 * marked noSeries. The week is flagged when the share is below 90 %; the comparison is exact (integer arithmetic),
 * so 90 % itself is not flagged.
 */
final class CoverageRules {

    /** The floor in percent. */
    static final int FLOOR_PERCENT = 90;

    private CoverageRules() {
    }

    static List<Model.Coverage> of(List<Model.Post> posts) {
        Map<String, int[]> counts = new TreeMap<>();
        Map<String, String> channelOf = new TreeMap<>();
        for (Model.Post post : posts) {
            String week = IsoWeek.of(java.time.Instant.parse(post.publishedAt()));
            String key = week + "|" + post.channelId();
            int[] pair = counts.computeIfAbsent(key, k -> new int[2]);
            pair[0]++;
            if (post.seriesId() != null || post.noSeries()) {
                pair[1]++;
            }
            channelOf.put(key, post.channelId());
        }
        List<Model.Coverage> rows = new ArrayList<>();
        counts.forEach((key, pair) -> {
            String week = key.substring(0, key.indexOf('|'));
            int shorts = pair[0];
            int covered = pair[1];
            double share = Rounding.to((double) covered / shorts, 2);
            boolean flagged = covered * 100 < shorts * FLOOR_PERCENT;
            rows.add(new Model.Coverage(channelOf.get(key), week, shorts, covered, share, flagged));
        });
        rows.sort(Comparator.comparing(Model.Coverage::week).thenComparing(Model.Coverage::channelId));
        return rows;
    }
}
