package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.brand.Colour;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostListener;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.SeriesAssignment.Flag;
import net.onelitefeather.menura.core.series.SeriesReports.BundleSeries;
import net.onelitefeather.menura.core.series.SeriesReports.CallToAction;
import net.onelitefeather.menura.core.series.SeriesReports.ChannelCoverage;
import net.onelitefeather.menura.core.series.SeriesReports.Episode;
import net.onelitefeather.menura.core.series.SeriesReports.Overview;
import net.onelitefeather.menura.core.series.SeriesReports.RenderBundle;
import net.onelitefeather.menura.core.series.SeriesReports.WeekCoverage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Content series: the catalogue, the checks that protect legibility, the assignment of external posts with their episode
 * numbers, the weekly coverage, the audience warning and the render bundle. Every method runs on the connection of a
 * transaction the caller opened, so an assignment made during an import commits with the import.
 *
 * <p>Coverage is computed on read; nothing here is scheduled. Video is never cut or rendered here (principle 2).
 */
public final class SeriesService implements ExternalPostListener {

    /** The WCAG 2.2 level AA threshold for normal text, which the badge text must reach against its series colour. */
    public static final double MIN_BADGE_CONTRAST = 4.5;

    /** A week below this share of covered shorts is flagged. */
    public static final int COVERAGE_PERCENT = 90;

    /** The format value that platform adapters report for a short. */
    public static final String SHORT_FORMAT = "short";

    /** The warning code shown while no active series targets players. */
    public static final String NO_PLAYERS_SERIES = "no_players_series";

    /** Weeks run from Monday in the zone of the team, so a week is the one people in the stream chat talk about. */
    public static final ZoneId WEEK_ZONE = ZoneId.of("Europe/Berlin");

    public static final int DEFAULT_WEEKS = 8;
    public static final int MAX_WEEKS = 52;
    static final int MAX_CHANNELS = 200;

    private final Clock clock;
    private final IdentifierGenerator identifiers;
    private final SeriesStore series;
    private final ExternalPostStore posts;
    private final ChannelStore channels;
    private final BrandTokens brand;
    private final JoinDomain joinDomain;

    public SeriesService(Clock clock, IdentifierGenerator identifiers, SeriesStore series, ExternalPostStore posts,
                         ChannelStore channels, BrandTokens brand, JoinDomain joinDomain) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
        this.series = Objects.requireNonNull(series, "series");
        this.posts = Objects.requireNonNull(posts, "posts");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.brand = Objects.requireNonNull(brand, "brand");
        this.joinDomain = Objects.requireNonNull(joinDomain, "joinDomain");
    }

    /** Every series, active and inactive. */
    public List<ContentSeries> list(Connection connection) throws SQLException {
        return series.list(connection);
    }

    /** One series, or 404. */
    public ContentSeries find(Connection connection, UUID id) throws SQLException {
        return series.find(connection, id).orElseThrow(SeriesService::seriesNotFound);
    }

    /** Stores a new series after its colours and template are checked against the brand kit. */
    public ContentSeries create(Connection connection, SeriesDraft draft) throws SQLException {
        Instant now = clock.instant();
        ContentSeries checked = ContentSeries.of(identifiers.next(), draft, now, now);
        requireBrandValues(checked);
        requireUniqueHashtag(connection, checked);
        series.insert(connection, checked);
        return checked;
    }

    /**
     * Replaces a series. The colours are checked again against the brand kit as it is now, so a changed colour that breaks
     * the contrast is refused and the stored series stays as it was.
     */
    public ContentSeries update(Connection connection, UUID id, SeriesDraft draft) throws SQLException {
        ContentSeries existing = find(connection, id);
        ContentSeries checked = ContentSeries.of(id, draft, existing.createdAt(), clock.instant());
        requireBrandValues(checked);
        requireUniqueHashtag(connection, checked);
        series.update(connection, checked);
        return checked;
    }

    /** The episodes of a series in number order. A post that no longer exists is left out. */
    public List<Episode> episodes(Connection connection, UUID seriesId) throws SQLException {
        find(connection, seriesId);
        List<Episode> episodes = new ArrayList<>();
        for (SeriesStore.Episode row : series.episodes(connection, seriesId)) {
            posts.find(connection, row.postId()).ifPresent(post -> episodes.add(new Episode(row.number(), post)));
        }
        return episodes;
    }

    /**
     * The decision on one post. Naming a series gives the post that series and the next number of it on the first
     * assignment; {@code noSeries} marks the post "no series". Moving a post that already belongs to a series or carries
     * the mark needs {@code replace}; without it the request is refused with 409 and nothing changes.
     */
    public SeriesAssignment assign(Connection connection, UUID postId, AssignmentCommand command, UUID actor)
            throws SQLException {
        Objects.requireNonNull(command, "command");
        ExternalPost post = posts.find(connection, postId).orElseThrow(() -> new ProblemException(404, "Not Found",
                "No external post with this id."));
        boolean hasSeries = command.seriesId() != null;
        if (hasSeries == command.noSeries()) {
            throw new ProblemException(400, "Invalid assignment",
                    "Name one series, or mark the post no series; not both and not neither.");
        }
        ContentSeries target = hasSeries ? find(connection, command.seriesId()) : null;
        Optional<SeriesAssignment> current = series.lockAssignment(connection, post.id());
        if (current.isPresent() && current.get().covered()) {
            if (sameDecision(current.get(), target)) {
                return current.get();
            }
            if (!command.replace()) {
                throw new ProblemException(409, "Conflict", "The post already belongs to a series or carries the "
                        + "no-series mark. Set replace to move it.");
            }
        }
        Instant now = clock.instant();
        SeriesAssignment assignment;
        if (target != null) {
            ensureEpisode(connection, target.id(), post.id());
            assignment = SeriesAssignment.toSeries(post.id(), target.id(), actor, now);
        } else {
            assignment = SeriesAssignment.markedNoSeries(post.id(), actor, now);
        }
        series.saveAssignment(connection, assignment);
        return assignment;
    }

    /** The decision on a post, if it has one. */
    public Optional<SeriesAssignment> assignmentOf(Connection connection, UUID postId) throws SQLException {
        return series.findAssignment(connection, postId);
    }

    /**
     * The import hook: a post the import has just stored is assigned to the one active series whose hashtag its description
     * contains. Two matching series flag the post for an editor and assign nothing. A post without a matching hashtag gets no
     * decision.
     */
    @Override
    public void imported(Connection connection, ExternalPost post, PublishedItem item) throws SQLException {
        String description = item == null ? "" : item.description();
        List<ContentSeries> matches = series.list(connection).stream()
                .filter(ContentSeries::active)
                .filter(candidate -> SeriesHashtags.contains(description, candidate.hashtag()))
                .toList();
        Instant now = clock.instant();
        if (matches.isEmpty()) {
            return;
        }
        if (matches.size() > 1) {
            series.saveAssignment(connection, SeriesAssignment.flagged(post.id(), Flag.TWO_SERIES, now));
            return;
        }
        ContentSeries only = matches.get(0);
        ensureEpisode(connection, only.id(), post.id());
        series.saveAssignment(connection, SeriesAssignment.toSeries(post.id(), only.id(), null, now));
    }

    /**
     * The series overview: every series, the warnings that apply, and the coverage of the last {@code weeks} weeks per
     * channel. Only published shorts count; a post with a publish time after now is not published yet.
     */
    public Overview overview(Connection connection, Integer weeks) throws SQLException {
        int count = weeks == null ? DEFAULT_WEEKS : weeks;
        if (count < 1 || count > MAX_WEEKS) {
            throw new ProblemException(400, "Invalid weeks", "The number of weeks must be from 1 to " + MAX_WEEKS + ".");
        }
        Instant now = clock.instant();
        List<ContentSeries> all = series.list(connection);
        List<String> warnings = all.stream().anyMatch(s -> s.active() && s.audience() == Audience.PLAYERS)
                ? List.of() : List.of(NO_PLAYERS_SERIES);
        Map<UUID, SeriesAssignment> byPost = new HashMap<>();
        for (SeriesAssignment assignment : series.listAssignments(connection)) {
            byPost.put(assignment.postId(), assignment);
        }
        LocalDate thisWeek = weekStart(now);
        List<ChannelCoverage> coverage = new ArrayList<>();
        for (Channel channel : channels.listChannels(connection, null, MAX_CHANNELS)) {
            Map<LocalDate, int[]> counts = new HashMap<>();
            for (ExternalPost post : posts.listForChannel(connection, channel.id())) {
                if (post.removed() || !SHORT_FORMAT.equals(post.format()) || post.publishedAt().isAfter(now)) {
                    continue;
                }
                LocalDate week = weekStart(post.publishedAt());
                if (week.isBefore(thisWeek.minusWeeks(count - 1L))) {
                    continue;
                }
                int[] tally = counts.computeIfAbsent(week, ignored -> new int[3]);
                tally[0]++;
                SeriesAssignment assignment = byPost.get(post.id());
                if (assignment != null && assignment.covered()) {
                    tally[1]++;
                }
                if (assignment != null && assignment.noSeries()) {
                    tally[2]++;
                }
            }
            List<WeekCoverage> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                LocalDate week = thisWeek.minusWeeks(i);
                int[] tally = counts.getOrDefault(week, new int[3]);
                rows.add(week(week, tally[0], tally[1], tally[2]));
            }
            coverage.add(new ChannelCoverage(channel.id(), channel.displayName(), rows));
        }
        return new Overview(all, warnings, coverage);
    }

    /**
     * The render bundle of a channel for the external renderer: every active series with its badge text, colour token,
     * opening formula and hashtag, and one call to action that names the join domain when one is configured.
     */
    public RenderBundle renderBundle(Connection connection, UUID channelId) throws SQLException {
        channels.findChannel(connection, channelId).orElseThrow(() -> new ProblemException(404, "Not Found",
                "The channel does not exist."));
        List<BundleSeries> active = series.list(connection).stream()
                .filter(ContentSeries::active)
                .map(s -> new BundleSeries(s.id(), s.name(), s.badgeText(), s.colourToken(), s.openingFormula(),
                        s.hashtag()))
                .toList();
        CallToAction callToAction = joinDomain.joinDomain()
                .filter(domain -> !domain.isBlank())
                .map(domain -> new CallToAction("Jetzt mitspielen auf " + domain))
                .orElse(null);
        return new RenderBundle(channelId, active, callToAction);
    }

    /** Gives the post its episode number in the series, once: a post that already has one keeps it. */
    private void ensureEpisode(Connection connection, UUID seriesId, UUID postId) throws SQLException {
        if (series.episodeOf(connection, seriesId, postId).isEmpty()) {
            series.saveEpisode(connection, seriesId, postId, series.nextEpisodeNumber(connection, seriesId));
        }
    }

    private void requireBrandValues(ContentSeries checked) throws SQLException {
        ColourToken colour = brandColour(checked.colourToken());
        ColourToken text = brandColour(checked.badgeToken());
        double ratio = Math.min(Colour.contrast(text.light(), colour.light()), Colour.contrast(text.dark(), colour.dark()));
        if (ratio < MIN_BADGE_CONTRAST) {
            throw new ProblemException(400, "Insufficient contrast", "The badge text token '" + checked.badgeToken()
                    + "' reaches a contrast ratio of " + ratio(ratio) + " against the series colour token '"
                    + checked.colourToken() + "'. Normal text needs at least 4.5:1 (WCAG 2.2 AA) in both roles.");
        }
        if (!brand.hasTemplate(checked.layoutTemplate())) {
            throw new ProblemException(400, "Invalid series", "The brand kit has no layout template '"
                    + checked.layoutTemplate() + "'.");
        }
    }

    private ColourToken brandColour(String name) throws SQLException {
        return brand.colour(name).filter(token -> Colour.isHex(token.light()) && Colour.isHex(token.dark()))
                .orElseThrow(() -> new ProblemException(400, "Invalid series",
                        "The brand kit has no colour token '" + name + "'."));
    }

    private void requireUniqueHashtag(Connection connection, ContentSeries checked) throws SQLException {
        boolean taken = series.list(connection).stream()
                .anyMatch(other -> other.hashtag().equals(checked.hashtag()) && !other.id().equals(checked.id()));
        if (taken) {
            throw new ProblemException(409, "Conflict", "Another series already uses this hashtag.");
        }
    }

    private static boolean sameDecision(SeriesAssignment current, ContentSeries target) {
        if (target == null) {
            return current.noSeries();
        }
        return target.id().equals(current.seriesId());
    }

    private static WeekCoverage week(LocalDate monday, int shorts, int covered, int noSeries) {
        Integer percent = shorts == 0 ? null : covered * 100 / shorts;
        boolean flagged = shorts > 0 && covered * 100 < shorts * COVERAGE_PERCENT;
        String label = String.format(Locale.ROOT, "%d-W%02d", monday.get(IsoFields.WEEK_BASED_YEAR),
                monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return new WeekCoverage(label, monday, shorts, covered, noSeries, percent, flagged);
    }

    private static LocalDate weekStart(Instant instant) {
        return instant.atZone(WEEK_ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The "ratio:1" text, rounded down so that a shown ratio below 4.5 is never shown as 4.5. */
    static String ratio(double ratio) {
        return String.format(Locale.ROOT, "%.2f:1", Math.floor(ratio * 100) / 100);
    }

    private static ProblemException seriesNotFound() {
        return new ProblemException(404, "Not Found", "No series with this id.");
    }

    /** A request to assign a post: one series, or the no-series mark, and whether an existing decision may be replaced. */
    public record AssignmentCommand(UUID seriesId, boolean noSeries, boolean replace) {
    }
}
