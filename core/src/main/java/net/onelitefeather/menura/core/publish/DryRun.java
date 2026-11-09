package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The schedule dry run (change add-content-distribution): checks a post against the platform's rules, the state of its
 * channel connection, the audit status of the platform app and the other posts of the channel. It is a pure function, so
 * the calendar can show its problems live and the publication job can run it again just before the platform is called.
 *
 * <p>An error blocks scheduling and publication; a warning (a collision) is shown on every post involved and does not block.
 */
public final class DryRun {

    public static final String ERROR = "error";
    public static final String WARNING = "warning";
    public static final String RECONNECT = "reconnect";

    private DryRun() {
    }

    /** One problem of a post. {@code action} names what the person can do, or is null. */
    public record Problem(String code, String severity, String message, String action) {
    }

    /**
     * The facts the check reads. {@code assetKind} is null when the asset is not a finished asset; {@code sameChannelPosts}
     * are the other posts of the channel, the post itself is ignored.
     */
    public record Input(Post post, ConnectionState channelState, String assetKind, PublishRules rules,
                        boolean publicAllowed, List<Post> sameChannelPosts) {
    }

    /** Every problem of the post, errors first. An empty list means the post is clean. */
    public static List<Problem> check(Input input) {
        Post post = input.post();
        PublishRules rules = input.rules();
        List<Problem> problems = new ArrayList<>();
        if (input.assetKind() == null) {
            problems.add(error("asset_missing", "The post has no finished asset."));
        } else if (!rules.kinds().contains(input.assetKind())) {
            problems.add(error("format_not_allowed", "The platform does not accept a " + input.assetKind()
                    + " for this channel."));
        }
        if (post.title().length() > rules.maxTitle()) {
            problems.add(error("title_too_long", "The title is longer than the " + rules.maxTitle()
                    + " characters of this channel."));
        }
        if (post.description().length() > rules.maxDescription()) {
            problems.add(error("description_too_long", "The text is longer than the " + rules.maxDescription()
                    + " characters of this channel."));
        }
        if (post.hashtags().size() > rules.maxHashtags()) {
            problems.add(error("too_many_hashtags", "The channel takes at most " + rules.maxHashtags() + " hashtags."));
        }
        if (input.channelState() != ConnectionState.CONNECTED) {
            problems.add(new Problem("connection_unusable", ERROR,
                    "The channel needs reconnection before this post can go out.", RECONNECT));
        }
        if (post.visibility() == Visibility.PUBLIC && !input.publicAllowed()) {
            problems.add(error("audit_restricted", "The platform app is not audited, so public visibility is not allowed."));
        }
        if (post.scheduledAt() == null) {
            problems.add(error("missing_time", "The post has no scheduled time."));
        } else {
            for (Post other : input.sameChannelPosts()) {
                if (!other.id().equals(post.id()) && post.scheduledAt().equals(other.scheduledAt())
                        && other.status() != PostStatus.FAILED) {
                    problems.add(new Problem("collision", WARNING,
                            "Another post of this channel is planned at the same time.", null));
                    break;
                }
            }
        }
        return List.copyOf(problems);
    }

    /**
     * The blocking problems of the explicit declarations of a post on a platform that requires them (YouTube, change
     * add-youtube-integration): the visibility must have been chosen and the made-for-kids answer given. No platform default
     * is relied on, so a missing answer blocks scheduling.
     */
    public static List<Problem> declarationProblems(YouTubeDeclaration declaration) {
        List<Problem> problems = new ArrayList<>();
        if (!declaration.visibilityDeclared()) {
            problems.add(error("visibility_undeclared", "Choose the visibility of this post before it is scheduled."));
        }
        if (declaration.madeForKids() == null) {
            problems.add(error("made_for_kids_missing", "Declare whether this video is made for kids before it is scheduled."));
        }
        return List.copyOf(problems);
    }

    /** The longest Short of YouTube in seconds; the same limit as YouTubeFormat.SHORT_LIMIT in the youtube package. */
    public static final int SHORT_MAX_SECONDS = 180;

    /**
     * The problems of a post planned as a YouTube Short (change add-youtube-integration, task 4.7). A Short is vertical or
     * square and at most three minutes long. The checks use the size and duration recorded at upload; where the asset records
     * neither, the post gets a warning and the metadata is not guessed. A post not planned as a Short has no problem here.
     */
    public static List<Problem> shortProblems(boolean plannedShort, Integer width, Integer height, Integer durationSeconds) {
        if (!plannedShort) {
            return List.of();
        }
        if (width == null || height == null || durationSeconds == null) {
            return List.of(new Problem("short_unverified", WARNING,
                    "The asset does not record its size or duration, so Menura cannot check that it is a Short.", null));
        }
        List<Problem> problems = new ArrayList<>();
        if (width > height) {
            problems.add(error("short_not_vertical", "A Short must be vertical or square; this asset is " + width + "x"
                    + height + "."));
        }
        if (durationSeconds > SHORT_MAX_SECONDS) {
            problems.add(error("short_too_long", "A Short may be at most " + SHORT_MAX_SECONDS + " seconds; this asset is "
                    + durationSeconds + " seconds."));
        }
        return List.copyOf(problems);
    }

    /** What is known about custom thumbnails on a channel: whether the platform allows them, and why not. */
    public record ThumbnailFact(boolean allowed, String reason) {
    }

    /**
     * The blocking problem of the playlist of a post (change add-youtube-integration, task 3.2): a playlist the channel does
     * not own is refused, and a playlist whose channel playlists could not be read is unverified, never called foreign. A
     * post without a playlist has no problem here.
     *
     * @param ownedPlaylists the playlists the channel owns, or empty when they could not be read
     */
    public static List<Problem> playlistProblems(String playlistId, Optional<Set<String>> ownedPlaylists) {
        if (playlistId == null) {
            return List.of();
        }
        if (ownedPlaylists.isEmpty()) {
            return List.of(error("playlist_unverified", "The playlist of this post could not be checked against the channel, "
                    + "so the post is not scheduled. Try again later."));
        }
        if (!ownedPlaylists.get().contains(playlistId)) {
            return List.of(error("playlist_not_owned", "The playlist of this post does not belong to this channel, so it is "
                    + "unavailable. Choose a playlist of this channel in the series."));
        }
        return List.of();
    }

    /**
     * The warning of a post with a thumbnail on a channel that may not set custom thumbnails (change add-youtube-integration,
     * task 4.6). It names the reason the platform gave. A channel whose eligibility was never learned gets no warning.
     */
    public static List<Problem> thumbnailProblems(boolean hasThumbnail, Optional<ThumbnailFact> fact) {
        if (!hasThumbnail || fact.isEmpty() || fact.get().allowed()) {
            return List.of();
        }
        String reason = fact.get().reason() == null || fact.get().reason().isBlank()
                ? "The platform does not allow custom thumbnails for this channel." : fact.get().reason();
        return List.of(new Problem("thumbnail_not_allowed", WARNING, "The thumbnail will not be applied. " + reason, null));
    }

    /** Whether any problem blocks scheduling or publication. */
    public static boolean blocking(List<Problem> problems) {
        return problems.stream().anyMatch(problem -> ERROR.equals(problem.severity()));
    }

    /** The codes of the blocking problems, for a fixed failure text. */
    public static Set<String> blockingCodes(List<Problem> problems) {
        return problems.stream().filter(problem -> ERROR.equals(problem.severity())).map(Problem::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Problem error(String code, String message) {
        return new Problem(code, ERROR, message, null);
    }
}
