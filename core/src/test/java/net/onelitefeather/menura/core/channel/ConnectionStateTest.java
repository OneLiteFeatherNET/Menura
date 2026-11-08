package net.onelitefeather.menura.core.channel;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionStateTest {

    private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T11:00:00Z");

    @Test
    void newChannelIsConnectedAndUsable() {
        Channel channel = connected();

        assertEquals(ConnectionState.CONNECTED, channel.state(), "a new connection starts connected");
        assertTrue(channel.usable(), "a connected channel may publish");
    }

    @Test
    void rejectedRefreshMovesConnectedToNeedsReconnectionWithReason() {
        Channel channel = connected().needsReconnection("refresh token rejected", T1);

        assertEquals(ConnectionState.NEEDS_RECONNECTION, channel.state());
        assertEquals("refresh token rejected", channel.stateReason(), "the reason is readable for planning");
        assertFalse(channel.usable(), "a channel that needs reconnection must not be used for new publications");
        assertEquals(T1, channel.updatedAt(), "the transition is timestamped with the injected clock");
    }

    @Test
    void reconnectMovesNeedsReconnectionBackToConnectedAndClearsReason() {
        Channel channel = connected().needsReconnection("revoked", T1)
                .reconnected(Set.of("https://www.googleapis.com/auth/youtube.upload"), Set.of("publish"), T1);

        assertEquals(ConnectionState.CONNECTED, channel.state());
        assertNull(channel.stateReason(), "a reconnected channel carries no stale reason");
        assertEquals(Set.of("https://www.googleapis.com/auth/youtube.upload"), channel.grantedScopes());
    }

    @Test
    void reconnectOnAConnectedChannelIsRefused() {
        assertThrows(IllegalStateException.class,
                () -> connected().reconnected(Set.of(), Set.of(), T1),
                "reconnection only replaces a grant that needs reconnection");
    }

    @Test
    void disconnectEndsBothConnectedAndNeedsReconnection() {
        assertEquals(ConnectionState.DISCONNECTED, connected().disconnected("revoked", T1).state());
        assertEquals(ConnectionState.DISCONNECTED,
                connected().needsReconnection("expired", T1).disconnected("failed", T1).state());
    }

    @Test
    void disconnectedChannelCannotBeMarkedOrReconnected() {
        Channel gone = connected().disconnected("not_supported", T1);

        assertThrows(IllegalStateException.class, () -> gone.needsReconnection("x", T1),
                "a disconnected channel never returns to needs reconnection");
        assertThrows(IllegalStateException.class, () -> gone.reconnected(Set.of(), Set.of(), T1),
                "a disconnected channel is not revived by a grant replacement");
    }

    @Test
    void disconnectRecordsWhetherThePlatformGrantMayStillExist() {
        Channel channel = connected().disconnected("failed", T1);

        assertEquals("failed", channel.grantRevocation(), "the record shows that the platform grant may still exist");
        assertEquals(T1, channel.disconnectedAt());
    }

    @Test
    void stateValuesAreStoredInTheirLowerCaseNames() {
        assertEquals("needs_reconnection", ConnectionState.NEEDS_RECONNECTION.value());
        assertEquals(ConnectionState.NEEDS_RECONNECTION, ConnectionState.parse("needs_reconnection"));
        assertThrows(IllegalArgumentException.class, () -> ConnectionState.parse("expired"),
                "an unknown state name is refused rather than guessed");
    }

    private static Channel connected() {
        return new Channel(UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"), "youtube", "UC123",
                "Main channel", ConnectionState.CONNECTED, null, Set.of("publish"),
                Set.of("https://www.googleapis.com/auth/youtube.upload"), null, T0, T0, null);
    }
}
