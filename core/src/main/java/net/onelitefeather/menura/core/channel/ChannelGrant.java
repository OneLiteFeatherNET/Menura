package net.onelitefeather.menura.core.channel;

import java.time.Instant;
import java.util.UUID;

/**
 * The tokens a platform granted for one channel. Only the store (which encrypts them) and the refresh logic read this
 * record. Its text form is redacted, so a grant that reaches a log line or an error report shows no token.
 */
public record ChannelGrant(UUID channelId, String accessToken, String refreshToken, Instant accessExpiresAt,
                           Instant updatedAt) {

    @Override
    public String toString() {
        return "ChannelGrant[channelId=" + channelId + ", tokens=redacted]";
    }
}
