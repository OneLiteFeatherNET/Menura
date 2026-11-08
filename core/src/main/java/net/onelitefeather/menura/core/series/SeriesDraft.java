package net.onelitefeather.menura.core.series;

import java.util.Map;
import java.util.UUID;

/**
 * What an editor sends for a series, before it is checked. Every field is still raw here; {@link ContentSeries#of}
 * checks the values and turns them into a series.
 *
 * @param badgeText    the badge text per language, for example {@code de} or {@code en-GB}
 * @param colourToken  the brand-kit token of the series colour
 * @param badgeToken   the brand-kit token of the badge text colour
 * @param audience     one of the audience names, see {@link Audience}
 * @param weekday      the preferred weekday, 1 (Monday) to 7 (Sunday)
 * @param postingSlot  the preferred posting hour: 12, 18 or 21
 * @param playlists    the playlist per channel, if any
 */
public record SeriesDraft(String name, Map<String, String> badgeText, String colourToken, String badgeToken,
                          String openingFormula, String layoutTemplate, String hashtag, String audience,
                          Integer weekday, Integer postingSlot, boolean active, Map<UUID, SeriesPlaylist> playlists) {

    public SeriesDraft {
        badgeText = badgeText == null ? null : Map.copyOf(badgeText);
        playlists = playlists == null ? Map.of() : Map.copyOf(playlists);
    }
}
