package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.AuditStatus;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** The facts the dry run reads for one post, gathered on one connection: the asset kind, the platform rules and the audit. */
final class PostFacts {

    /** The platform rules of this build, by platform identifier. A platform without rules fails the dry run. */
    static final Map<String, PublishRules> RULES = Map.of("youtube", PublishRules.YOUTUBE);

    private PostFacts() {
    }

    /** The problems of a post on its channel, read at this moment. */
    static List<DryRun.Problem> problems(Connection connection, PostStore posts, ChannelStore channels, Post post,
                                         Channel channel) throws SQLException {
        PublishRules rules = RULES.get(channel.platform());
        if (rules == null) {
            return List.of(new DryRun.Problem("platform_unsupported", DryRun.ERROR,
                    "The platform of this channel is not supported in this build.", null));
        }
        String kind = posts.finishedAssetKind(connection, post.assetId()).orElse(null);
        boolean audited = channels.findAuditStatus(connection, channel.platform())
                .map(status -> status.status() == AuditStatus.AUDITED).orElse(false);
        List<Post> sameTime = post.scheduledAt() == null ? List.of()
                : posts.scheduledOn(connection, channel.id(), post.scheduledAt(), post.scheduledAt().plus(Duration.ofSeconds(1)));
        return DryRun.check(new DryRun.Input(post, channel.state(), kind, rules, audited, sameTime));
    }

    /** The rules of a platform, or null when this build has none. */
    static PublishRules rules(String platform) {
        return RULES.get(platform);
    }
}
