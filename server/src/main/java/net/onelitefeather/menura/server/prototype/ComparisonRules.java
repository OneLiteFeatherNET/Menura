package net.onelitefeather.menura.server.prototype;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Series comparison of one week. Rows are sorted by engaged views, highest first, never by the number of views; ties
 * break on the series id. An average over no value is null.
 */
final class ComparisonRules {

    private ComparisonRules() {
    }

    static List<Model.SeriesComparison> compare(String week, List<Model.Series> series, List<Model.Post> posts) {
        List<Model.SeriesComparison> rows = new ArrayList<>();
        for (Model.Series candidate : series) {
            List<Model.Post> mine = posts.stream()
                    .filter(post -> candidate.id().equals(post.seriesId()))
                    .filter(post -> week.equals(IsoWeek.of(Instant.parse(post.publishedAt()))))
                    .toList();
            long engaged = mine.stream().mapToLong(Model.Post::engagedViews).sum();
            OptionalDouble average = mine.stream().map(Model.Post::avgViewPercent).filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue).average();
            double estimate = mine.stream().mapToDouble(Model.Post::estimatedFirstJoins).sum();
            rows.add(new Model.SeriesComparison(candidate.id(), candidate.name(), candidate.audience(), mine.size(),
                    engaged, average.isPresent() ? Rounding.to(average.getAsDouble(), 1) : null,
                    Rounding.to(estimate, 2), true));
        }
        rows.sort(Comparator.comparingLong(Model.SeriesComparison::engagedViews).reversed()
                .thenComparing(Model.SeriesComparison::seriesId));
        return rows;
    }
}
