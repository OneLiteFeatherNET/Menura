package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A recurring content series: the badge, opening formula and hashtag that make its shorts recognisable, and the
 * preferences a planner reads. Colours are references to brand-kit tokens. The contrast of the badge text against the
 * series colour is checked by the service, which holds the brand kit; this record checks the shape of every value.
 */
public record ContentSeries(UUID id, String name, Map<String, String> badgeText, String colourToken, String badgeToken,
                            String openingFormula, String layoutTemplate, String hashtag, Audience audience,
                            int weekday, int postingSlot, boolean active, Map<UUID, SeriesPlaylist> playlists,
                            Set<Role> visibleTo, Instant createdAt, Instant updatedAt) {

    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");
    private static final Pattern TEMPLATE = Pattern.compile("[a-z0-9-]{1,40}");
    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]{2,49}");

    static final int NAME_MAX = 80;
    static final int BADGE_MAX = 40;
    static final int FORMULA_MAX = 120;
    static final int PLAYLIST_MAX = 200;

    public ContentSeries {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        badgeText = Map.copyOf(Objects.requireNonNull(badgeText, "badgeText"));
        playlists = Map.copyOf(Objects.requireNonNull(playlists, "playlists"));
        visibleTo = Set.copyOf(Objects.requireNonNull(visibleTo, "visibleTo"));
        if (visibleTo.isEmpty()) {
            throw new IllegalArgumentException("a series is visible to at least one role");
        }
    }

    /** Whether a caller holding these roles may see the series. */
    public boolean isVisibleTo(Set<Role> roles) {
        return roles.stream().anyMatch(visibleTo::contains);
    }

    /**
     * A series from a draft, checked. A failure is a 400 Problem Details error that names the field and the allowed
     * values; nothing is stored in that case.
     */
    public static ContentSeries of(UUID id, SeriesDraft draft, Instant createdAt, Instant updatedAt) {
        Objects.requireNonNull(draft, "draft");
        String name = text(draft.name(), "name", NAME_MAX);
        Map<String, String> badges = badges(draft.badgeText());
        String colour = token(draft.colourToken(), "colourToken");
        String badge = token(draft.badgeToken(), "badgeToken");
        String formula = text(draft.openingFormula(), "openingFormula", FORMULA_MAX);
        String template = matching(draft.layoutTemplate(), "layoutTemplate", TEMPLATE,
                "a layout template name of lower case letters, digits and hyphens, at most 40 characters");
        String hashtag = hashtag(draft.hashtag());
        Audience audience = Audience.parse(draft.audience()).orElseThrow(() -> invalid(
                "The audience must be one of: " + Audience.allowedValues() + "."));
        int weekday = draft.weekday() == null ? 0 : draft.weekday();
        if (weekday < 1 || weekday > 7) {
            throw invalid("The weekday must be a number from 1 (Monday) to 7 (Sunday).");
        }
        int slot = draft.postingSlot() == null ? 0 : draft.postingSlot();
        if (slot != 12 && slot != 18 && slot != 21) {
            throw invalid("The posting slot must be 12, 18 or 21 o'clock.");
        }
        Map<UUID, SeriesPlaylist> playlists = new LinkedHashMap<>();
        draft.playlists().forEach((channel, playlist) -> playlists.put(channel, playlist(playlist)));
        return new ContentSeries(id, name, badges, colour, badge, formula, template, hashtag, audience, weekday, slot,
                draft.active(), playlists, visibility(draft.visibleTo()), createdAt, updatedAt);
    }

    /** The roles of a draft's visibility; null means every role, an empty list or an unknown name is refused. */
    private static Set<Role> visibility(List<String> names) {
        if (names == null) {
            return EnumSet.allOf(Role.class);
        }
        if (names.isEmpty()) {
            throw invalid("The series needs at least one role that may see it.");
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String name : names) {
            Role role = Role.parse(name).orElseThrow(() -> invalid("The role '" + name + "' is unknown. Allowed roles: "
                    + Arrays.stream(Role.values()).map(r -> r.name().toLowerCase(Locale.ROOT)).toList() + "."));
            roles.add(role);
        }
        return roles;
    }

    private static Map<String, String> badges(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw invalid("A series needs a badge text in at least one language.");
        }
        Map<String, String> badges = new LinkedHashMap<>();
        raw.forEach((language, value) -> {
            if (language == null || !LANGUAGE.matcher(language).matches()) {
                throw invalid("A language of the badge text must look like de or en-GB.");
            }
            badges.put(language, text(value, "badgeText." + language, BADGE_MAX));
        });
        return badges;
    }

    private static SeriesPlaylist playlist(SeriesPlaylist playlist) {
        if (playlist == null) {
            throw invalid("A playlist needs an identifier and a name.");
        }
        return new SeriesPlaylist(text(playlist.playlistId(), "playlistId", PLAYLIST_MAX),
                text(playlist.playlistName(), "playlistName", PLAYLIST_MAX));
    }

    private static String token(String value, String field) {
        if (value != null && value.startsWith("#")) {
            throw invalid("A series colour is a reference to a colour token of the brand kit, not a hex value.");
        }
        return matching(value, field, TOKEN, "a colour token name of the brand kit, such as primary");
    }

    private static String hashtag(String value) {
        if (value == null || !HASHTAG.matcher(value).matches()) {
            throw invalid("The hashtag starts with # and has 2 to 49 letters, digits or underscores.");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw invalid("The field " + field + " is required.");
        }
        if (value.length() > max) {
            throw invalid("The field " + field + " is longer than " + max + " characters.");
        }
        return value;
    }

    private static String matching(String value, String field, Pattern pattern, String expected) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw invalid("The field " + field + " must be " + expected + ".");
        }
        return value;
    }

    private static ProblemException invalid(String detail) {
        return new ProblemException(400, "Invalid series", detail);
    }
}
