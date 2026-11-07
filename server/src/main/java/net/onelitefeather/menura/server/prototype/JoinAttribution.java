package net.onelitefeather.menura.server.prototype;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Join attribution. Measured first joins are counted per week and channel (exact); a first join through a channel
 * host is also split evenly across the channel's posts published in the 72 hours before the join, which gives an
 * estimate per post. A join with no post in its window, and a direct join, are attributed to no post. Repeat joins
 * are ignored.
 */
final class JoinAttribution {

    static final Duration WINDOW = Duration.ofHours(72);

    private static final String DIRECT = "direct";

    private JoinAttribution() {
    }

    /** The estimated first joins per post id. Only posts that received a share appear. */
    static Map<String, Double> estimates(List<Model.Post> posts, List<Model.Channel> channels,
                                         List<Model.JoinEvent> joins) {
        Map<String, Double> shares = new HashMap<>();
        for (Model.JoinEvent join : joins) {
            if (!join.firstJoin()) {
                continue;
            }
            Optional<String> channelId = Hosts.channelIdFor(channels, join.host());
            if (channelId.isEmpty()) {
                continue;
            }
            Instant from = join.at().minus(WINDOW);
            List<Model.Post> eligible = posts.stream()
                    .filter(post -> post.channelId().equals(channelId.get()))
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

    /** Measured first joins per ISO week, channel and direct. Rows with no first join are not listed. */
    static List<Model.WeeklyAttribution> weekly(List<Model.Channel> channels, List<Model.JoinEvent> joins) {
        TreeMap<Bucket, Integer> counts = new TreeMap<>(Comparator.comparing(Bucket::week)
                .thenComparingInt(Bucket::order));
        for (Model.JoinEvent join : joins) {
            if (!join.firstJoin()) {
                continue;
            }
            String week = IsoWeek.of(join.at());
            Optional<Model.Channel> channel = channelFor(channels, join.host());
            Bucket bucket = channel
                    .map(c -> new Bucket(week, channels.indexOf(c), c.id(), c.platform()))
                    .orElse(new Bucket(week, Integer.MAX_VALUE, null, DIRECT));
            counts.merge(bucket, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new Model.WeeklyAttribution(e.getKey().week(), e.getKey().platform(),
                        e.getKey().channelId(), e.getValue()))
                .toList();
    }

    private static Optional<Model.Channel> channelFor(List<Model.Channel> channels, String host) {
        return Hosts.channelIdFor(channels, host).flatMap(id -> channels.stream()
                .filter(channel -> channel.id().equals(id)).findFirst());
    }

    private record Bucket(String week, int order, String channelId, String platform) {
    }
}
