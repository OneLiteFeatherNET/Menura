package net.onelitefeather.menura.server.attribution;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.attribution.JoinAnswer;
import net.onelitefeather.menura.core.attribution.JoinEvent;
import net.onelitefeather.menura.core.attribution.JoinReports;
import net.onelitefeather.menura.core.attribution.JoinSettings;
import net.onelitefeather.menura.core.attribution.StatedSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The JSON views of the join attribution. Members the contract marks nullable are always written (JsonInclude.ALWAYS). */
public final class JoinViews {

    private JoinViews() {
    }

    /** The admin's request: the domain, and optionally the window in hours. */
    @Serdeable
    public record SettingsRequest(String joinDomain, Integer attributionWindowHours) {
    }

    /** The join settings of the installation. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SettingsView(String joinDomain, int attributionWindowHours, Instant updatedAt) {

        static SettingsView of(JoinSettings settings) {
            return new SettingsView(settings.joinDomain(), settings.attributionWindowHours(), settings.updatedAt());
        }
    }

    /** A join event as stored; it names no player. */
    @Serdeable
    public record EventReceipt(UUID eventId, boolean firstJoin, Instant joinedAt) {

        static EventReceipt of(JoinEvent event) {
            return new EventReceipt(event.eventId(), event.firstJoin(), event.joinedAt());
        }
    }

    /** A source answer as stored. */
    @Serdeable
    public record AnswerReceipt(UUID eventId, String source, Instant answeredAt) {

        static AnswerReceipt of(JoinAnswer answer) {
            return new AnswerReceipt(answer.eventId(), answer.source().wire(), answer.answeredAt());
        }
    }

    /** One stated source of a period: the number of first joins that named it, labelled as stated by players. */
    @Serdeable
    public record StatedView(String source, long joins, String label) {
    }

    /** One period of the new-player report. The answer rate is null for a period without first joins. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PeriodView(LocalDate start, LocalDate end, long total, long unanswered, Double answerRate,
                             List<StatedView> stated) {
    }

    /** The new players of a range, in date order. */
    @Serdeable
    public record NewPlayersView(String grouping, LocalDate from, LocalDate to, List<PeriodView> periods) {
    }

    /** The estimated first joins of one post. */
    @Serdeable
    public record PostEstimateView(UUID postId, double estimatedFirstJoins, int windowHours, String label) {
    }

    /** One series of the series estimates; the seriesId is null for the posts without a series. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeriesEstimateView(UUID seriesId, String name, double estimatedFirstJoins) {
    }

    /** The series estimates of a range. */
    @Serdeable
    public record SeriesEstimatesView(LocalDate from, LocalDate to, String label, List<SeriesEstimateView> series) {
    }

    static NewPlayersView of(JoinReports.NewPlayers report) {
        List<PeriodView> periods = new ArrayList<>();
        for (JoinReports.Period period : report.periods()) {
            List<StatedView> stated = new ArrayList<>();
            for (Map.Entry<StatedSource, Long> entry : period.stated().entrySet()) {
                stated.add(new StatedView(entry.getKey().wire(), entry.getValue(), JoinReports.STATED_LABEL));
            }
            periods.add(new PeriodView(period.start(), period.end(), period.total(), period.unanswered(),
                    period.answerRate(), stated));
        }
        return new NewPlayersView(report.grouping().wire(), report.from(), report.to(), periods);
    }

    static SeriesEstimatesView of(JoinReports.SeriesEstimates estimates) {
        List<SeriesEstimateView> series = estimates.series().stream()
                .map(entry -> new SeriesEstimateView(entry.seriesId(), entry.name(), entry.estimatedFirstJoins()))
                .toList();
        return new SeriesEstimatesView(estimates.from(), estimates.to(), JoinReports.ESTIMATE_LABEL, series);
    }
}
