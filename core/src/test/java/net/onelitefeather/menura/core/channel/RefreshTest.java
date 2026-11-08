package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.ScriptedPlatform;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Refresh before expiry, rotation, rejected refresh, outages and the publication guard, on a fake clock. */
class RefreshTest {

    private static final String REFRESH = "1//old-refresh";
    private static final Instant SOON = ChannelFixture.NOW.plus(Duration.ofMinutes(5));
    private static final Instant LATER = ChannelFixture.NOW.plus(Duration.ofHours(1));

    @Test
    void healthRunRefreshesAnAccessTokenThatExpiresWithinTheMargin() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC1", SOON, REFRESH);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.fresh", null, 3600, ChannelFixture.YOUTUBE_READONLY)));

        ConnectionService.HealthReport report = world.service.checkHealth();

        assertEquals(1, report.refreshed(), "the token inside the margin is refreshed by the health run");
        assertEquals("ya29.fresh", world.storedAccessToken(channel), "the new access token replaces the old one");
        assertEquals(ChannelFixture.NOW.plusSeconds(3600),
                world.store.storedGrant(channel).orElseThrow().accessExpiresAt(), "the new expiry is stored");
    }

    @Test
    void healthRunLeavesATokenOutsideTheMarginAlone() throws Exception {
        ChannelFixture world = new ChannelFixture();
        world.seedConnected("UC2", LATER, REFRESH);

        ConnectionService.HealthReport report = world.service.checkHealth();

        assertEquals(0, report.refreshed(), "a token that is still valid is not refreshed");
        assertTrue(world.http.sent().isEmpty(), "no request reaches the token endpoint");
    }

    @Test
    void rotatedRefreshTokenIsStoredForTheNextRefresh() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC3", SOON, REFRESH);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.r", "1//rotated", 3600, ChannelFixture.YOUTUBE_READONLY)));

        world.service.checkHealth();

        assertEquals("1//rotated", world.store.storedGrant(channel).orElseThrow().refreshToken(),
                "the rotated refresh token replaces the previous one");
    }

    @Test
    void rejectedRefreshMarksTheChannelNeedsReconnectionWithAReasonAndNoToken() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC4", SOON, REFRESH);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(400, "{\"error\":\"invalid_grant\"}"));

        ConnectionService.HealthReport report = world.service.checkHealth();

        Channel stored = world.store.storedChannel(channel).orElseThrow();
        assertEquals(ConnectionState.NEEDS_RECONNECTION, stored.state(), "a rejected refresh needs reconnection");
        assertEquals(1, report.needsReconnection());
        assertTrue(stored.stateReason() != null && !stored.stateReason().isBlank(), "the reason is recorded");
        assertFalse(stored.stateReason().contains(REFRESH), "the reason never carries a token");
    }

    @Test
    void platformOutageKeepsTheChannelConnected() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC5", SOON, REFRESH);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(503, ""));

        ConnectionService.HealthReport report = world.service.checkHealth();

        assertEquals(ConnectionState.CONNECTED, world.store.storedChannel(channel).orElseThrow().state(),
                "an outage is not a revoked grant; the next run tries again");
        assertEquals(1, report.unavailable());
    }

    @Test
    void networkFailureDuringRefreshKeepsTheChannelConnected() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC6", SOON, REFRESH);
        world.http.unreachable("POST", "/token", new IOException("connection reset"));

        world.service.checkHealth();

        assertEquals(ConnectionState.CONNECTED, world.store.storedChannel(channel).orElseThrow().state());
    }

    @Test
    void publicationRefreshesATokenNearExpiryBeforeItUsesIt() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC7", SOON, REFRESH);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.at-publish", null, 3600, ChannelFixture.YOUTUBE_READONLY)));

        ChannelGrant grant = world.service.freshGrant(channel);

        assertEquals("ya29.at-publish", grant.accessToken(),
                "a publication never uses a token that expires inside the margin");
    }

    @Test
    void publicationIsRefusedWithoutAnyRequestWhenTheChannelNeedsReconnection() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC8", SOON, REFRESH);
        world.store.updateChannel(null, world.store.storedChannel(channel).orElseThrow()
                .needsReconnection("revoked at the platform", ChannelFixture.NOW));

        ProblemException failure = assertThrows(ProblemException.class, () -> world.service.freshGrant(channel));

        assertEquals(409, failure.status(), "a publication on a channel that needs reconnection is refused");
        assertTrue(world.http.sent().isEmpty(), "no request is sent for a refused publication");
    }

    @Test
    void rejectedRefreshDuringPublicationMarksTheChannelAndRefuses() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC9", SOON, REFRESH);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(401, "{\"error\":\"unauthorized_client\"}"));

        assertThrows(ProblemException.class, () -> world.service.freshGrant(channel));

        assertEquals(ConnectionState.NEEDS_RECONNECTION, world.store.storedChannel(channel).orElseThrow().state(),
                "the state change survives the refusal");
    }

    @Test
    void errorOutputOfAFailedRefreshHoldsNoTokenValue() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UC10", SOON, "1//SECRET-REFRESH-VALUE");
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(400,
                "{\"error\":\"invalid_grant\",\"echo\":\"1//SECRET-REFRESH-VALUE\"}"));

        ProblemException failure = assertThrows(ProblemException.class, () -> world.service.freshGrant(channel));

        assertFalse(failure.getMessage().contains("SECRET"), "the refusal carries no token");
        assertFalse(failure.detail().contains("SECRET"), "the Problem Details detail carries no token");
    }
}
