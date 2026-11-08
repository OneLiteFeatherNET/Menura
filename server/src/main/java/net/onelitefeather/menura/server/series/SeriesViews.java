package net.onelitefeather.menura.server.series;

import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.series.ContentSeries;
import net.onelitefeather.menura.core.series.SeriesAssignment;
import net.onelitefeather.menura.core.series.SeriesDraft;
import net.onelitefeather.menura.core.series.SeriesPlaylist;
import net.onelitefeather.menura.core.series.SeriesReports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The representations of content series over REST: the requests an editor sends and the views every role reads. */
public final class SeriesViews {

    private SeriesViews() {
    }

    /** A series as an editor sends it. Null fields are refused by the core check, except active, which defaults to true. */
    @Serdeable
    public record SeriesRequest(String name, Map<String, String> badgeText, String colourToken, String badgeToken,
                                String openingFormula, String layoutTemplate, String hashtag, String audience,
                                Integer weekday, Integer postingSlot, Boolean active, List<PlaylistRequest> playlists) {
    }

    /** The playlist of a series on one channel. */
    @Serdeable
    public record PlaylistRequest(UUID channelId, String playlistId, String playlistName) {
    }

    /** One assignment request: a series, or the no-series mark, and whether an existing decision may be replaced. */
    @Serdeable
    public record AssignRequest(UUID seriesId, boolean noSeries, boolean replace) {
    }

    @Serdeable
    public record SeriesView(UUID id, String name, Map<String, String> badgeText, String colourToken, String badgeToken,
                             String openingFormula, String layoutTemplate, String hashtag, String audience, int weekday,
                             int postingSlot, boolean active, List<PlaylistView> playlists, Instant createdAt,
                             Instant updatedAt) {
    }

    @Serdeable
    public record PlaylistView(UUID channelId, String playlistId, String playlistName) {
    }

    @Serdeable
    public record SeriesDetailView(SeriesView series, List<EpisodeView> episodes) {
    }

    /** One episode: its number, its post and the playlist state of that post. */
    @Serdeable
    public record EpisodeView(int number, UUID postId, String title, String format, Instant publishedAt,
                              String playlistState) {
    }

    @Serdeable
    public record AssignmentView(UUID postId, UUID seriesId, boolean noSeries, String flag, String playlistState,
                                 Instant playlistCheckedAt, Instant assignedAt, UUID assignedBy) {
    }

    @Serdeable
    public record OverviewView(List<SeriesView> series, List<String> warnings, List<ChannelCoverageView> coverage) {
    }

    @Serdeable
    public record ChannelCoverageView(UUID channelId, String displayName, List<WeekView> weeks) {
    }

    @Serdeable
    public record WeekView(String isoWeek, LocalDate weekStart, int shorts, int covered, int noSeries, Integer percent,
                           boolean flagged) {
    }

    @Serdeable
    public record RenderBundleView(UUID channelId, List<BundleSeriesView> series, String callToAction) {
    }

    @Serdeable
    public record BundleSeriesView(UUID id, String name, Map<String, String> badgeText, String colourToken,
                                   String openingFormula, String hashtag) {
    }

    public static SeriesDraft draft(SeriesRequest request) {
        Map<UUID, SeriesPlaylist> playlists = new LinkedHashMap<>();
        if (request.playlists() != null) {
            for (PlaylistRequest playlist : request.playlists()) {
                if (playlist != null && playlist.channelId() != null) {
                    playlists.put(playlist.channelId(), new SeriesPlaylist(playlist.playlistId(), playlist.playlistName()));
                }
            }
        }
        return new SeriesDraft(request.name(), request.badgeText(), request.colourToken(), request.badgeToken(),
                request.openingFormula(), request.layoutTemplate(), request.hashtag(), request.audience(),
                request.weekday(), request.postingSlot(), request.active() == null || request.active(), playlists);
    }

    public static SeriesView of(ContentSeries series) {
        List<PlaylistView> playlists = series.playlists().entrySet().stream()
                .map(e -> new PlaylistView(e.getKey(), e.getValue().playlistId(), e.getValue().playlistName()))
                .toList();
        return new SeriesView(series.id(), series.name(), series.badgeText(), series.colourToken(), series.badgeToken(),
                series.openingFormula(), series.layoutTemplate(), series.hashtag(), series.audience().value(),
                series.weekday(), series.postingSlot(), series.active(), playlists, series.createdAt(), series.updatedAt());
    }

    public static AssignmentView of(SeriesAssignment assignment) {
        return new AssignmentView(assignment.postId(), assignment.seriesId(), assignment.noSeries(),
                lower(assignment.flag()), lower(assignment.playlistState()), assignment.playlistCheckedAt(),
                assignment.assignedAt(), assignment.assignedBy());
    }

    public static OverviewView of(SeriesReports.Overview overview) {
        return new OverviewView(overview.series().stream().map(SeriesViews::of).toList(), overview.warnings(),
                overview.coverage().stream().map(c -> new ChannelCoverageView(c.channelId(), c.displayName(),
                        c.weeks().stream().map(w -> new WeekView(w.isoWeek(), w.weekStart(), w.shorts(), w.covered(),
                                w.noSeries(), w.percent(), w.flagged())).toList())).toList());
    }

    public static RenderBundleView of(SeriesReports.RenderBundle bundle) {
        return new RenderBundleView(bundle.channelId(), bundle.series().stream()
                .map(s -> new BundleSeriesView(s.id(), s.name(), s.badgeText(), s.colourToken(), s.openingFormula(),
                        s.hashtag()))
                .toList(), bundle.callToAction() == null ? null : bundle.callToAction().text());
    }

    static EpisodeView episode(SeriesReports.Episode episode, SeriesAssignment assignment) {
        return new EpisodeView(episode.number(), episode.post().id(), episode.post().title(), episode.post().format(),
                episode.post().publishedAt(), assignment == null ? null : lower(assignment.playlistState()));
    }

    static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
