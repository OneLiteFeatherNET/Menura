package net.onelitefeather.menura.core.series;

import java.time.Instant;
import java.util.UUID;

/**
 * The series decision on one external post. A post has at most one of: a series, the explicit "no series" mark, or a flag
 * that asks an editor to decide (two series hashtags). The playlist state is set by the playlist check of a series post.
 *
 * <p>Every change returns a new record. The episode number is not held here: it lives with the series and the post.
 *
 * @param assignedBy the editor who made the decision, or null when the import made it
 */
public record SeriesAssignment(UUID postId, UUID seriesId, boolean noSeries, Flag flag, PlaylistState playlistState,
                               Instant playlistCheckedAt, Instant assignedAt, UUID assignedBy) {

    /** A reason an editor must decide on a post. */
    public enum Flag { TWO_SERIES }

    /** What the playlist check found for a series post. */
    public enum PlaylistState { IN_PLAYLIST, MISSING, NOT_CHECKED }

    public SeriesAssignment {
        if (seriesId != null && (noSeries || flag != null)) {
            throw new IllegalArgumentException("a post with a series has neither the no-series mark nor a flag");
        }
        if (noSeries && flag != null) {
            throw new IllegalArgumentException("a post marked no series is not flagged");
        }
    }

    public static SeriesAssignment toSeries(UUID postId, UUID seriesId, UUID actor, Instant at) {
        return new SeriesAssignment(postId, seriesId, false, null, null, null, at, actor);
    }

    public static SeriesAssignment markedNoSeries(UUID postId, UUID actor, Instant at) {
        return new SeriesAssignment(postId, null, true, null, null, null, at, actor);
    }

    public static SeriesAssignment flagged(UUID postId, Flag flag, Instant at) {
        return new SeriesAssignment(postId, null, false, flag, null, null, at, null);
    }

    /** Whether the post is covered by a decision of a series or by the explicit "no series" mark. */
    public boolean covered() {
        return seriesId != null || noSeries;
    }

    /** The same decision with the result of a playlist check. */
    public SeriesAssignment withPlaylistState(PlaylistState state, Instant checkedAt) {
        return new SeriesAssignment(postId, seriesId, noSeries, flag, state, checkedAt, assignedAt, assignedBy);
    }
}
