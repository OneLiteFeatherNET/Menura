package net.onelitefeather.menura.server.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.channel.ChannelSnapshot;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformFeature;
import net.onelitefeather.menura.core.channel.PlatformSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The bodies of the channel endpoints. None of them has a field for a token, a secret or a token fragment: a token
 * cannot reach a response because no type here can hold one.
 */
public final class ChannelViews {

    private ChannelViews() {
    }

    /** One page of channels; {@code nextCursor} is null on the last page. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChannelPage(List<ChannelView> items, String nextCursor) {
    }

    /** A channel with its connection state, scopes, quota and the audit status of its platform app. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChannelView(UUID id, String platform, String displayName, String platformAccountId, String state,
                              String stateReason, List<String> features, List<String> grantedScopes,
                              List<String> missingScopes, String grantRevocation, Instant createdAt, Instant updatedAt,
                              QuotaView quota, AuditView audit) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QuotaView(Long used, Long limit, Instant resetAt, boolean warning, DelayView delay) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DelayView(String reason, Instant nextAttemptAt) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AuditView(String status, String displayText, List<String> restrictions, Instant decidedAt,
                            String source) {
    }

    /** A platform with what it supports and the features a channel can enable. */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlatformView(String platform, String displayName, CapabilitiesView capabilities,
                               List<FeatureView> features, AuditView audit) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CapabilitiesView(boolean publishing, boolean nativeScheduling, boolean listing, boolean metrics,
                                   boolean revocation) {
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FeatureView(String name, List<String> scopes) {
    }

    /** The consent URL the browser is sent to. It carries the state of this flow only. */
    @Serdeable
    public record Authorization(String authorizationUrl) {
    }

    /** Features to enable for a new connection. */
    @Serdeable
    public record ConnectRequest(String platform, List<String> features) {
    }

    /** Features to enable when a channel is reconnected or extended; empty keeps the channel's features. */
    @Serdeable
    public record ConsentRequest(List<String> features) {
    }

    /** The outcome of a disconnect: what the platform did with the grant. */
    @Serdeable
    public record Disconnection(String revocation) {
    }

    @Serdeable
    public record AuditStatusRequest(String status, String source) {
    }

    static ChannelView of(ChannelSnapshot snapshot) {
        var channel = snapshot.channel();
        return new ChannelView(channel.id(), channel.platform(), channel.displayName(), channel.platformAccountId(),
                channel.state().value(), channel.stateReason(), List.copyOf(channel.features().stream().sorted().toList()),
                channel.grantedScopes().stream().sorted().toList(), snapshot.missingScopes().stream().sorted().toList(),
                channel.grantRevocation(), channel.createdAt(), channel.updatedAt(), quotaOf(snapshot.quota()),
                auditOf(snapshot.audit()));
    }

    static PlatformView of(PlatformSnapshot platform) {
        PlatformCapabilities capabilities = platform.capabilities();
        return new PlatformView(platform.platform(), platform.displayName(),
                new CapabilitiesView(capabilities.publishing(), capabilities.nativeScheduling(), capabilities.listing(),
                        capabilities.metrics(), capabilities.revocation()),
                platform.features().stream().map(ChannelViews::featureOf).toList(), auditOf(platform.audit()));
    }

    static AuditView auditOf(ChannelSnapshot.AuditView audit) {
        return new AuditView(audit.status().value(), audit.displayText(), audit.restrictions(), audit.decidedAt(),
                audit.source());
    }

    private static FeatureView featureOf(PlatformFeature feature) {
        return new FeatureView(feature.name(), feature.scopes().stream().sorted().toList());
    }

    private static QuotaView quotaOf(ChannelSnapshot.QuotaView quota) {
        ChannelSnapshot.Delay delay = quota.delay();
        return new QuotaView(quota.used(), quota.limit(), quota.resetAt(), quota.warning(),
                delay == null ? null : new DelayView(delay.reason(), delay.nextAttemptAt()));
    }
}
