package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quota visibility per connection, rate-limit delays, and the audit status per platform app. */
class QuotaAndAuditTest {

    private static final Instant SOON = ChannelFixture.NOW.plus(Duration.ofHours(1));

    @Test
    void quotaAboveTheWarningLevelIsFlaggedWithItsResetTime() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCq1", SOON, "1//r");
        Instant reset = ChannelFixture.NOW.plus(Duration.ofHours(6));

        world.service.recordQuota(channel, 9000, 10000, reset);

        ChannelSnapshot.QuotaView quota = world.service.snapshot(channel).quota();
        assertTrue(quota.warning(), "90 percent of the daily quota is above the warning level of 80 percent");
        assertEquals(Long.valueOf(9000L), quota.used());
        assertEquals(Long.valueOf(10000L), quota.limit());
        assertEquals(reset, quota.resetAt(), "the remaining quota comes with its reset time");
    }

    @Test
    void quotaBelowTheWarningLevelIsNotFlagged() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCq2", SOON, "1//r");

        world.service.recordQuota(channel, 1000, 10000, ChannelFixture.NOW.plus(Duration.ofHours(6)));

        assertFalse(world.service.snapshot(channel).quota().warning());
    }

    @Test
    void rateLimitShowsTheDelayReasonAndTheNextAttempt() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCq3", SOON, "1//r");
        Instant until = ChannelFixture.NOW.plus(Duration.ofMinutes(30));

        world.service.recordRateLimit(channel, until, "platform rate limit");

        ChannelSnapshot.Delay delay = world.service.snapshot(channel).quota().delay();
        assertEquals("platform rate limit", delay.reason(), "the delay reason is shown");
        assertEquals(until, delay.nextAttemptAt(), "the time of the next attempt is shown");
    }

    @Test
    void anExpiredRateLimitIsNoLongerShown() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channel = world.seedConnected("UCq4", SOON, "1//r");
        world.service.recordRateLimit(channel, ChannelFixture.NOW.plus(Duration.ofMinutes(5)), "limit");
        world.clock.advance(Duration.ofMinutes(10));

        assertNull(world.service.snapshot(channel).quota().delay(), "a limit that has passed delays nothing");
    }

    @Test
    void unrecordedAuditStatusIsUnknownAndRestrictsPublicVisibility() throws Exception {
        ChannelFixture world = new ChannelFixture();

        ChannelSnapshot.AuditView view = world.service.platforms().get(0).audit();

        assertEquals(AuditStatus.UNKNOWN, view.status(), "a status nobody recorded is unknown");
        assertEquals("unknown", view.displayText());
        assertTrue(view.restrictions().contains("public visibility not available"),
                "an unknown status assumes no restriction is lifted");
        assertFalse(world.service.publicVisibilityAllowed("youtube"));
    }

    @Test
    void unauditedAppShowsUploadsPrivateOnlyAndRestrictsPublicVisibility() throws Exception {
        ChannelFixture world = new ChannelFixture();
        world.service.setAuditStatus(ChannelFixture.ACTOR, "youtube", AuditStatus.UNAUDITED, "API console, 2026-11-02");

        ChannelSnapshot.AuditView view = world.service.platforms().get(0).audit();

        assertEquals("unaudited: uploads private only", view.displayText());
        assertEquals(java.util.List.of("public visibility not available"), view.restrictions());
        assertFalse(world.service.publicVisibilityAllowed("youtube"));
    }

    @Test
    void auditedAppHasNoRestriction() throws Exception {
        ChannelFixture world = new ChannelFixture();
        world.service.setAuditStatus(ChannelFixture.ACTOR, "youtube", AuditStatus.AUDITED, "Google audit letter");

        ChannelSnapshot.AuditView view = world.service.platforms().get(0).audit();

        assertTrue(view.restrictions().isEmpty(), "an audited app restricts nothing");
        assertTrue(world.service.publicVisibilityAllowed("youtube"));
    }

    @Test
    void settingAuditStatusRecordsDateSourceAndAnAuditEntry() throws Exception {
        ChannelFixture world = new ChannelFixture();

        world.service.setAuditStatus(ChannelFixture.ACTOR, "youtube", AuditStatus.UNAUDITED, "Console review");

        ChannelSnapshot.AuditView view = world.service.platforms().get(0).audit();
        assertEquals(ChannelFixture.NOW, view.decidedAt(), "the decision carries the date it was recorded");
        assertEquals("Console review", view.source(), "the decision carries its source");
        assertEquals("platform_audit_status.set", world.audit.entries().get(0).action());
    }

    @Test
    void auditSourceWithControlCharactersIsRefused() throws Exception {
        ChannelFixture world = new ChannelFixture();

        assertEquals(400, assertThrows(ProblemException.class, () -> world.service.setAuditStatus(
                ChannelFixture.ACTOR, "youtube", AuditStatus.AUDITED, "line\nbreak")).status());
    }

    @Test
    void auditStatusOfAnUnknownPlatformIsRefused() throws Exception {
        ChannelFixture world = new ChannelFixture();

        assertEquals(400, assertThrows(ProblemException.class, () -> world.service.setAuditStatus(
                ChannelFixture.ACTOR, "myspace", AuditStatus.AUDITED, "x")).status());
    }
}
