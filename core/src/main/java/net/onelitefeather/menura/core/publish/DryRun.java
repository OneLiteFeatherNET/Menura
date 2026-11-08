package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;

import java.util.ArrayList;
import java.util.List;
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
