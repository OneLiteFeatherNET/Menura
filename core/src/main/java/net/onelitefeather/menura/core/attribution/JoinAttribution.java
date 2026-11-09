package net.onelitefeather.menura.core.attribution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The estimate of which posts preceded each first join (change add-join-attribution). Pure: no I/O, so the rule is tested
 * without a database.
 *
 * <p>A first join is given to the posts of all channels published within the window before it, split evenly. A join
 * answered with a platform is restricted to the posts of that platform in the window; when that platform has no post in
 * the window, no post receives the join. Every figure is an estimate.
 */
public final class JoinAttribution {

    private JoinAttribution() {
    }

    /**
     * The estimated first joins per post. A post that received no join is absent from the map. A post counts for a join
     * when it was published no earlier than the join minus the window and no later than the join.
     */
    public static Map<UUID, Double> perPost(List<FirstJoin> joins, List<AttributablePost> posts, Duration window) {
        Map<UUID, Double> estimates = new LinkedHashMap<>();
        for (FirstJoin join : joins) {
            List<AttributablePost> candidates = candidates(join, posts, window);
            List<AttributablePost> eligible = restricted(join, candidates);
            if (eligible.isEmpty()) {
                continue;
            }
            double share = 1.0 / eligible.size();
            for (AttributablePost post : eligible) {
                estimates.merge(post.postId(), share, Double::sum);
            }
        }
        return estimates;
    }

    /** The posts inside the window before the join, in the order they were given. */
    static List<AttributablePost> candidates(FirstJoin join, List<AttributablePost> posts, Duration window) {
        Instant earliest = join.joinedAt().minus(window);
        List<AttributablePost> inWindow = new ArrayList<>();
        for (AttributablePost post : posts) {
            Instant published = post.publishedAt();
            if (!published.isBefore(earliest) && !published.isAfter(join.joinedAt())) {
                inWindow.add(post);
            }
        }
        return inWindow;
    }

    private static List<AttributablePost> restricted(FirstJoin join, List<AttributablePost> candidates) {
        if (join.source() == null || !join.source().isPlatform()) {
            return candidates;
        }
        String platform = join.source().wire();
        return candidates.stream().filter(post -> platform.equals(post.platform())).toList();
    }
}
