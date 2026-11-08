package net.onelitefeather.menura.core.channel;

/**
 * What a platform adapter supports. Common code checks these flags instead of the platform name.
 *
 * @param publishing       the adapter can publish an item
 * @param nativeScheduling the platform accepts a future publication time itself; a channel without it refuses scheduling
 * @param listing          the adapter can list the channel's existing published items
 * @param metrics          the adapter can read metrics of published items
 * @param revocation       the platform offers token revocation, requested on disconnect
 */
public record PlatformCapabilities(boolean publishing, boolean nativeScheduling, boolean listing, boolean metrics,
                                   boolean revocation) {
}
