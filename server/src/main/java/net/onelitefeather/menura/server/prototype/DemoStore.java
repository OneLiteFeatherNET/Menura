package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The in-memory state of the prototype: series, posts with their series and episode numbers, and the join events.
 * It is seeded from {@link DemoSeed} and is thread-safe: every public method holds the one lock of the store, so a
 * read never sees half of a write. Time comes from the injected clock only.
 */
public final class DemoStore {

    private static final Set<String> AUDIENCES = Set.of("developers", "mixed", "players");
    private static final Pattern HASHTAG = Pattern.compile("^#[\\p{L}\\p{N}_]+$");
    private static final Pattern SLOT = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final Clock clock;
    private final List<Model.Channel> channels;
    private final Model.BrandKit brandKit;
    private final List<Model.JoinEvent> joins;
    private final Map<String, Model.Series> series = new LinkedHashMap<>();
    private final Map<String, Model.Post> posts = new LinkedHashMap<>();
    private final Map<String, Integer> lastEpisode = new HashMap<>();

    public DemoStore(Clock clock) {
        this.clock = clock;
        this.channels = DemoSeed.channels();
        this.brandKit = DemoSeed.brandKit();
        this.joins = DemoSeed.joins();
        for (Model.Series seeded : DemoSeed.series()) {
            series.put(seeded.id(), seeded);
        }
        List<Model.SeedPost> ordered = DemoSeed.posts().stream()
                .sorted(Comparator.comparing((Model.SeedPost p) -> Instant.parse(p.publishedAt())).thenComparing(Model.SeedPost::id))
                .toList();
        for (Model.SeedPost seed : ordered) {
            String seriesId = seed.noSeries() ? null
                    : Assignment.automaticSeries(seed.hashtags(), List.copyOf(series.values())).orElse(null);
            Integer episode = seriesId == null ? null : nextEpisode(seriesId);
            posts.put(seed.id(), new Model.Post(seed.id(), seed.title(), seed.channelId(), platformOf(seed.channelId()),
                    seed.publishedAt(), seed.hashtags(), seriesId, seed.noSeries(), episode, seed.engagedViews(),
                    seed.avgViewPercent(), 0.0, seed.inPlaylist()));
        }
    }

    public synchronized List<Model.Channel> channels() {
        return channels;
    }

    public synchronized Model.BrandKit brandKit() {
        return brandKit;
    }

    public synchronized List<Model.Series> series() {
        return series.values().stream().map(s -> s.withEpisodes(countPostsOf(s.id()))).toList();
    }

    public synchronized Model.RenderBundle renderBundle() {
        return new Model.RenderBundle(brandKit.version(), brandKit,
                series().stream().filter(Model.Series::active).toList(), channels);
    }

    /** The posts, oldest first, optionally filtered by series and by channel. */
    public synchronized List<Model.Post> posts(String seriesId, String channelId) {
        return withEstimates().stream()
                .map(post -> post.withEstimate(Rounding.to(post.estimatedFirstJoins(), 2)))
                .filter(post -> seriesId == null || seriesId.equals(post.seriesId()))
                .filter(post -> channelId == null || channelId.equals(post.channelId()))
                .sorted(Comparator.comparing((Model.Post p) -> Instant.parse(p.publishedAt())).thenComparing(Model.Post::id))
                .toList();
    }

    public synchronized Model.Series createSeries(Model.SeriesRequest request) {
        require(request.name(), "name");
        require(request.hashtag(), "hashtag");
        require(request.colorToken(), "colorToken");
        require(request.badgeTextColor(), "badgeTextColor");
        require(request.weekday(), "weekday");
        require(request.slot(), "slot");
        if (request.badge() == null) {
            throw unprocessable("badge is required.");
        }
        if (!HASHTAG.matcher(request.hashtag()).matches()) {
            throw unprocessable("hashtag " + request.hashtag() + " must start with # and hold letters or digits.");
        }
        for (Model.Series existing : series.values()) {
            if (existing.hashtag().equalsIgnoreCase(request.hashtag())) {
                throw unprocessable("hashtag " + request.hashtag() + " is already used by series " + existing.id()
                        + ".");
            }
        }
        Optional<Model.Token> token = brandKit.tokens().stream()
                .filter(t -> t.name().equals(request.colorToken())).findFirst();
        if (token.isEmpty()) {
            throw unprocessable("colour token " + request.colorToken() + " is unknown.");
        }
        String colour = token.get().light();
        if (!Contrast.isHex(request.badgeTextColor())) {
            throw unprocessable("badge text colour " + request.badgeTextColor() + " must be #RRGGBB.");
        }
        double ratio = Contrast.ratio(request.badgeTextColor(), colour);
        if (ratio < Contrast.MINIMUM) {
            throw unprocessable(String.format(Locale.ROOT,
                    "badge text colour %s on series colour %s has a contrast of %.2f:1; at least 4.5:1 is required.",
                    request.badgeTextColor(), colour, ratio));
        }
        if (!AUDIENCES.contains(request.audience())) {
            throw unprocessable("audience " + request.audience() + " is unknown.");
        }
        if (!isWeekday(request.weekday())) {
            throw unprocessable("weekday " + request.weekday() + " is unknown.");
        }
        if (!SLOT.matcher(request.slot()).matches()) {
            throw unprocessable("slot " + request.slot() + " must be HH:mm.");
        }
        Model.Series created = new Model.Series(uniqueId(request.name()), request.name(), request.badge(),
                request.colorToken(), colour, request.badgeTextColor(),
                request.openingFormula() == null ? "" : request.openingFormula(), request.hashtag(),
                request.audience(), request.weekday(), request.slot(),
                request.playlists() == null ? Map.of() : Map.copyOf(request.playlists()), 0,
                request.active() == null || request.active());
        series.put(created.id(), created);
        return created.withEpisodes(0);
    }

    /**
     * Sets the series of a post, or marks it as noSeries. A new series gives the next episode number of that series;
     * a number that was handed out is never handed out again.
     */
    public synchronized Model.Post assign(String postId, Model.AssignRequest request) {
        Model.Post current = posts.get(postId);
        if (current == null) {
            throw new ProblemException(404, "Not Found", "No post with the id " + postId + ".");
        }
        Model.Post updated;
        if (request.noSeries()) {
            if (request.seriesId() != null) {
                throw unprocessable("a post cannot be marked noSeries and assigned to a series at once.");
            }
            updated = current.withAssignment(null, true, null);
        } else if (request.seriesId() == null) {
            updated = current.withAssignment(null, false, null);
        } else {
            if (!series.containsKey(request.seriesId())) {
                throw unprocessable("series " + request.seriesId() + " is unknown.");
            }
            updated = request.seriesId().equals(current.seriesId()) ? current
                    : current.withAssignment(request.seriesId(), false, nextEpisode(request.seriesId()));
        }
        posts.put(postId, updated);
        return withEstimates().stream().filter(post -> post.id().equals(postId)).findFirst()
                .map(post -> post.withEstimate(Rounding.to(post.estimatedFirstJoins(), 2)))
                .orElseThrow();
    }

    public synchronized List<Model.Coverage> coverage() {
        return CoverageRules.of(withEstimates());
    }

    public synchronized List<Model.WeeklyAttribution> attribution() {
        return JoinAttribution.weekly(channels, joins);
    }

    /** The comparison of one week; the latest week that has posts when {@code week} is null. */
    public synchronized List<Model.SeriesComparison> comparison(String week) {
        if (week != null && !IsoWeek.isWellFormed(week)) {
            throw unprocessable("week " + week + " must look like 2026-W45.");
        }
        String wanted = week != null ? week : latestWeek().orElse(null);
        if (wanted == null) {
            return List.of();
        }
        return ComparisonRules.compare(wanted, series(), withEstimates());
    }

    public synchronized List<Model.Warning> warnings() {
        List<Model.Post> all = withEstimates();
        return WarningRules.of(CoverageRules.of(all), series(), all, Instant.now(clock));
    }

    private List<Model.Post> withEstimates() {
        Map<String, Double> estimates = JoinAttribution.estimates(List.copyOf(posts.values()), channels, joins);
        return posts.values().stream().map(post -> post.withEstimate(estimates.getOrDefault(post.id(), 0.0))).toList();
    }

    private Optional<String> latestWeek() {
        return posts.values().stream().map(post -> IsoWeek.of(Instant.parse(post.publishedAt())))
                .max(Comparator.naturalOrder());
    }

    private int countPostsOf(String seriesId) {
        return (int) posts.values().stream().filter(post -> seriesId.equals(post.seriesId())).count();
    }

    private int nextEpisode(String seriesId) {
        return lastEpisode.merge(seriesId, 1, Integer::sum);
    }

    private String uniqueId(String name) {
        String base = slug(name);
        String candidate = base;
        int suffix = 2;
        while (series.containsKey(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slug(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "series" : slug;
    }

    private static boolean isWeekday(String value) {
        try {
            DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw unprocessable(field + " is required.");
        }
    }

    private static ProblemException unprocessable(String detail) {
        return new ProblemException(422, "Unprocessable Content", detail);
    }

    private String platformOf(String channelId) {
        return channels.stream().filter(c -> c.id().equals(channelId)).findFirst().orElseThrow().platform();
    }
}
