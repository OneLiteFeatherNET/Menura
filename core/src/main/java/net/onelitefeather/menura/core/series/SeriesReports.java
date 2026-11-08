package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.post.ExternalPost;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The read models of the series: the overview with coverage and warnings, and the render bundle of a channel. */
public final class SeriesReports {

    private SeriesReports() {
    }

    /**
     * The series overview: every series, the warning codes that apply, and the weekly coverage of every channel.
     */
    public record Overview(List<ContentSeries> series, List<String> warnings, List<ChannelCoverage> coverage) {
    }

    /** The coverage of one channel, newest week first. */
    public record ChannelCoverage(UUID channelId, String displayName, List<WeekCoverage> weeks) {
    }

    /**
     * One calendar week (Monday to Sunday, Europe/Berlin) of published shorts. {@code percent} is null for a week without
     * shorts, which is never flagged.
     */
    public record WeekCoverage(String isoWeek, LocalDate weekStart, int shorts, int covered, int noSeries,
                               Integer percent, boolean flagged) {
    }

    /** What the external renderer reads for one channel: the active series and one call to action, if a join domain exists. */
    public record RenderBundle(UUID channelId, List<BundleSeries> series, CallToAction callToAction) {
    }

    /** One active series as the renderer needs it. The colour is the name of a brand-kit token. */
    public record BundleSeries(UUID id, String name, Map<String, String> badgeText, String colourToken,
                               String openingFormula, String hashtag) {
    }

    /** The one call-to-action text of a bundle; it names the join domain. */
    public record CallToAction(String text) {
    }

    /** An episode of a series: its number and the post that holds it. */
    public record Episode(int number, ExternalPost post) {
    }
}
