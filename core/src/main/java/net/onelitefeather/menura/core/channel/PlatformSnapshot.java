package net.onelitefeather.menura.core.channel;

import java.util.List;

/** A platform as the server offers it: what it supports, the features a channel can enable, and its audit status. */
public record PlatformSnapshot(String platform, String displayName, PlatformCapabilities capabilities,
                               List<PlatformFeature> features, ChannelSnapshot.AuditView audit) {
}
