package net.onelitefeather.menura.server.prototype;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The warnings the editor sees: flagged coverage, a series post missing from its series playlist 24 hours after
 * publishing, and no active series for the player audience. Coverage comes first, then playlist, then players.
 */
final class WarningRules {

    static final Duration PLAYLIST_GRACE = Duration.ofHours(24);

    private WarningRules() {
    }

    static List<Model.Warning> of(List<Model.Coverage> coverage, List<Model.Series> series, List<Model.Post> posts,
                                  Instant now) {
        List<Model.Warning> warnings = new ArrayList<>();
        for (Model.Coverage row : coverage) {
            if (row.flagged()) {
                long percent = Math.round(row.covered() * 100.0 / row.shorts());
                warnings.add(new Model.Warning("coverage",
                        row.channelId() + ": " + row.week() + " nur " + percent + " % der Shorts in einer Serie"));
            }
        }
        for (Model.Post post : posts.stream()
                .sorted(Comparator.comparing((Model.Post p) -> Instant.parse(p.publishedAt())).thenComparing(Model.Post::id))
                .toList()) {
            if (missingFromPlaylist(post, series, now)) {
                warnings.add(new Model.Warning("playlist",
                        "\"" + post.title() + "\" fehlt 24 h nach Veröffentlichung in der Serien-Playlist"));
            }
        }
        noPlayerSeries(series).ifPresent(warnings::add);
        return warnings;
    }

    /** The warning for a missing player series, empty when an active series targets players. */
    static Optional<Model.Warning> noPlayerSeries(List<Model.Series> series) {
        boolean hasPlayerSeries = series.stream().anyMatch(s -> s.active() && "players".equals(s.audience()));
        return hasPlayerSeries ? Optional.empty()
                : Optional.of(new Model.Warning("no-player-series",
                "Keine aktive Serie für die Zielgruppe Spieler."));
    }

    private static boolean missingFromPlaylist(Model.Post post, List<Model.Series> series, Instant now) {
        if (post.seriesId() == null || post.inPlaylist()) {
            return false;
        }
        Optional<Model.Series> owner = series.stream().filter(s -> s.id().equals(post.seriesId())).findFirst();
        if (owner.isEmpty() || !owner.get().playlists().containsKey(post.channelId())) {
            return false;
        }
        return !now.isBefore(Instant.parse(post.publishedAt()).plus(PLAYLIST_GRACE));
    }
}
