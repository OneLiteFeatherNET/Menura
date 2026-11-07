package net.onelitefeather.menura.server.prototype;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The bodies of the prototype API (contract in the prototype notes; field names are fixed by the web app). Every
 * record is plain data: the rules live in the rule classes and in {@link DemoStore}.
 */
public final class Model {

    private Model() {
    }

    @Serdeable
    public record Localized(String de, String en) {
    }

    @Serdeable
    public record Channel(String id, String platform, String name) {
    }

    /**
     * A content series. {@code episodes} is the number of posts assigned to it; {@code color} is the light colour of
     * {@code colorToken} in the brand kit.
     */
    @Serdeable
    public record Series(String id, String name, Localized badge, String colorToken, String color,
                         String badgeTextColor, String openingFormula, String hashtag, String audience,
                         String weekday, String slot, Map<String, String> playlists, int episodes, boolean active) {

        public Series withEpisodes(int count) {
            return new Series(id, name, badge, colorToken, color, badgeTextColor, openingFormula, hashtag, audience,
                    weekday, slot, playlists, count, active);
        }

        public Series withActive(boolean value) {
            return new Series(id, name, badge, colorToken, color, badgeTextColor, openingFormula, hashtag, audience,
                    weekday, slot, playlists, episodes, value);
        }
    }

    /** The body of POST /api/prototype/series. {@code color} is accepted and ignored: it follows from the token. */
    @Serdeable
    public record SeriesRequest(String name, Localized badge, String colorToken, String badgeTextColor,
                                String openingFormula, String hashtag, String audience, String weekday, String slot,
                                Map<String, String> playlists, Boolean active, String color) {
    }

    /**
     * A short. {@code publishedAt} is an ISO-8601 instant in UTC. {@code estimatedFirstJoins} is rounded to two
     * decimals in responses; {@code avgViewPercent} is null where the platform does not deliver it.
     */
    @Serdeable
    public record Post(String id, String title, String channelId, String platform, String publishedAt,
                       List<String> hashtags, String seriesId, boolean noSeries, Integer episode, long engagedViews,
                       Double avgViewPercent, double estimatedFirstJoins, boolean inPlaylist) {

        public Post withEstimate(double value) {
            return new Post(id, title, channelId, platform, publishedAt, hashtags, seriesId, noSeries, episode,
                    engagedViews, avgViewPercent, value, inPlaylist);
        }

        public Post withAssignment(String newSeriesId, boolean newNoSeries, Integer newEpisode) {
            return new Post(id, title, channelId, platform, publishedAt, hashtags, newSeriesId, newNoSeries,
                    newEpisode, engagedViews, avgViewPercent, estimatedFirstJoins, inPlaylist);
        }
    }

    /** The body of PUT /api/prototype/posts/{id}/series. */
    @Serdeable
    public record AssignRequest(String seriesId, boolean noSeries) {
    }

    @Serdeable
    public record Coverage(String channelId, String week, int shorts, int covered, double share, boolean flagged) {
    }

    /**
     * The measured first joins of one week. {@code answered} counts the first joins that named a source, and
     * {@code answerRate} is answered / firstJoins rounded to two decimals. {@code bySource} lists every source in
     * the fixed order of {@link JoinAttribution#SOURCES}, zeros included.
     */
    @Serdeable
    public record WeeklyAttribution(String week, int firstJoins, int answered, double answerRate,
                                    List<SourceCount> bySource) {
    }

    /** The first joins of one stated source (a platform, friends, other or unanswered). */
    @Serdeable
    public record SourceCount(String source, int firstJoins) {
    }

    @Serdeable
    public record SeriesComparison(String seriesId, String name, String audience, int posts, long engagedViews,
                                   Double avgViewPercent, double estimatedFirstJoins, boolean estimate) {
    }

    @Serdeable
    public record Warning(String kind, String message) {
    }

    @Serdeable
    public record Token(String name, String light, String dark) {
    }

    @Serdeable
    public record Mark(String name, int safeAreaPercent) {
    }

    @Serdeable
    public record SafeZone(int topPercent, int bottomPercent) {
    }

    @Serdeable
    public record Slot(String name, Integer fromSeconds, Integer toSeconds, Integer lastSeconds, int x, int y, int w,
                       int h, List<String> params) {
    }

    @Serdeable
    public record Template(String id, int width, int height, SafeZone safeZone, List<Slot> slots) {
    }

    /** {@code joinDomain} is the one domain all players join over; {@code cta} is the one call to action. */
    @Serdeable
    public record BrandKit(int version, String source, List<Token> tokens, List<Mark> marks,
                           List<Template> templates, String joinDomain, Localized cta) {
    }

    @Serdeable
    public record RenderBundle(int kitVersion, BrandKit brandKit, String joinDomain, Localized cta,
                               List<Series> series, List<Channel> channels) {
    }

    /**
     * One join as the join service reported it. {@code answer} is what the player named in the in-game question on
     * the first join; null when unanswered. No player identifier exists in this model. Not served.
     */
    public record JoinEvent(String answer, boolean firstJoin, Instant at) {
    }

    /** One short in the seed, before the assignment rule has run. Not served. */
    public record SeedPost(String id, String channelId, String publishedAt, String title, List<String> hashtags,
                           boolean noSeries, long engagedViews, Double avgViewPercent, boolean inPlaylist) {
    }
}
