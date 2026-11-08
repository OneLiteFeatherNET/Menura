package net.onelitefeather.menura.core.channel;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A channel with what its page shows: the token-free channel, the scopes an enabled feature is missing, quota and
 * rate-limit state, and the audit status of its platform app.
 */
public record ChannelSnapshot(Channel channel, Set<String> missingScopes, QuotaView quota, AuditView audit) {

    /** Quota as the platform reported or Menura measured it. Null values were never reported. */
    public record QuotaView(Long used, Long limit, Instant resetAt, boolean warning, Delay delay) {
    }

    /** A rate limit that delays publications on this channel until {@code nextAttemptAt}. */
    public record Delay(String reason, Instant nextAttemptAt) {
    }

    /** The audit status of the platform app, its readable text and the restrictions it implies. */
    public record AuditView(AuditStatus status, String displayText, List<String> restrictions, Instant decidedAt,
                            String source) {
    }
}
