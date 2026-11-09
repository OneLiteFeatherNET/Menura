package net.onelitefeather.menura.server.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.publish.ChannelQuotaService.QuotaView;

import java.time.Instant;

/** The remaining daily quota of a YouTube channel. Every member is always present (JsonInclude.ALWAYS). */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChannelQuotaView(int unitsPerDay, int unitsRemaining, int uploadsPerDay, int uploadsRemaining,
                               Instant resetsAt) {

    public static ChannelQuotaView of(QuotaView quota) {
        return new ChannelQuotaView(quota.unitsPerDay(), quota.unitsRemaining(), quota.uploadsPerDay(),
                quota.uploadsRemaining(), quota.resetsAt());
    }
}
