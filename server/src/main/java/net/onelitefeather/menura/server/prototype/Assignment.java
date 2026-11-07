package net.onelitefeather.menura.server.prototype;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Automatic series assignment at import: a post is assigned when its hashtags name exactly one series. Two series
 * hashtags, or none, leave the post unassigned for a person to decide.
 */
final class Assignment {

    private Assignment() {
    }

    static Optional<String> automaticSeries(List<String> hashtags, List<Model.Series> series) {
        Set<String> matched = new LinkedHashSet<>();
        for (String tag : hashtags) {
            for (Model.Series candidate : series) {
                if (candidate.hashtag().equalsIgnoreCase(tag)) {
                    matched.add(candidate.id());
                }
            }
        }
        return matched.size() == 1 ? Optional.of(matched.iterator().next()) : Optional.empty();
    }
}
