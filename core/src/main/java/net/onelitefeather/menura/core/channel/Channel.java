package net.onelitefeather.menura.core.channel;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A channel of a platform account that Menura publishes to. The representation carries no token: tokens live in
 * {@link ChannelGrant}, which only the store and the refresh logic read.
 *
 * <p>The state transitions are the only way to change {@link #state()}; each returns a new channel stamped with the
 * time the caller read from its injected clock. An illegal transition is a programming error and throws.
 */
public record Channel(UUID id, String platform, String platformAccountId, String displayName, ConnectionState state,
                      String stateReason, Set<String> features, Set<String> grantedScopes, String grantRevocation,
                      Instant createdAt, Instant updatedAt, Instant disconnectedAt) {

    public Channel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(platformAccountId, "platformAccountId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(state, "state");
        features = Set.copyOf(Objects.requireNonNull(features, "features"));
        grantedScopes = Set.copyOf(Objects.requireNonNull(grantedScopes, "grantedScopes"));
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Whether new publications may use this channel. */
    public boolean usable() {
        return state == ConnectionState.CONNECTED;
    }

    /** A refresh was rejected or the platform reported the grant revoked. Only a connected channel can need it. */
    public Channel needsReconnection(String reason, Instant at) {
        requireState(ConnectionState.CONNECTED, "mark as needing reconnection");
        return with(ConnectionState.NEEDS_RECONNECTION, reason, grantRevocation, disconnectedAt, at);
    }

    /** A new consent for the same platform account replaced the grant. Clears the reason of the earlier problem. */
    public Channel reconnected(Set<String> scopes, Set<String> enabledFeatures, Instant at) {
        requireState(ConnectionState.NEEDS_RECONNECTION, "reconnect");
        return new Channel(id, platform, platformAccountId, displayName, ConnectionState.CONNECTED, null,
                enabledFeatures, scopes, grantRevocation, createdAt, at, disconnectedAt);
    }

    /** A connected channel took a new grant with more scopes or features, through a new consent. */
    public Channel scopesChanged(Set<String> scopes, Set<String> enabledFeatures, Instant at) {
        requireState(ConnectionState.CONNECTED, "change scopes");
        return new Channel(id, platform, platformAccountId, displayName, state, stateReason, enabledFeatures, scopes,
                grantRevocation, createdAt, at, disconnectedAt);
    }

    /**
     * The channel was disconnected. {@code revocation} records what happened at the platform: revoked, not_supported
     * or failed. A failed revocation means the platform grant may still exist.
     */
    public Channel disconnected(String revocation, Instant at) {
        if (state == ConnectionState.DISCONNECTED) {
            throw new IllegalStateException("a disconnected channel cannot be disconnected again");
        }
        return with(ConnectionState.DISCONNECTED, null, revocation, at, at);
    }

    private void requireState(ConnectionState expected, String action) {
        if (state != expected) {
            throw new IllegalStateException("cannot " + action + " a channel that is " + state.value());
        }
    }

    private Channel with(ConnectionState next, String reason, String revocation, Instant disconnectedAt, Instant at) {
        return new Channel(id, platform, platformAccountId, displayName, next, reason, features, grantedScopes,
                revocation, createdAt, at, disconnectedAt);
    }
}
