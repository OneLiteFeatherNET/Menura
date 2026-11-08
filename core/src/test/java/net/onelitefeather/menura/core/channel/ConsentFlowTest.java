package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.ScriptedPlatform;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The consent start and callback against a fake platform consent and token endpoint. */
class ConsentFlowTest {

    private static final String READONLY = ChannelFixture.YOUTUBE_READONLY;
    private static final String UPLOAD = ChannelFixture.YOUTUBE_UPLOAD;

    @Test
    void successfulConsentCreatesAConnectedChannelWithTheGrantedScopes() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        answerTokenAndIdentity(world, READONLY + " " + UPLOAD, "UCmain", "Main channel");

        ConsentOutcome outcome = world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "auth-code", null);

        assertEquals(ConsentOutcome.Kind.CONNECTED, outcome.kind());
        Channel channel = world.store.storedChannel(outcome.channelId()).orElseThrow();
        assertEquals(ConnectionState.CONNECTED, channel.state(), "a successful consent leaves the channel connected");
        assertEquals("UCmain", channel.platformAccountId());
        assertEquals("Main channel", channel.displayName());
        assertEquals(Set.of(READONLY, UPLOAD), channel.grantedScopes(), "the scopes the platform granted are shown");
        assertEquals(Set.of("publish"), channel.features(), "the features the channel is enabled for are stored");
        assertEquals("ya29.access", world.store.storedGrant(channel.id()).orElseThrow().accessToken());
        assertEquals("channel.connect", world.audit.entries().get(0).action(), "the connection is audited");
    }

    @Test
    void consentRefusedCreatesNoChannelAndReportsTheReason() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);

        ConsentOutcome outcome = world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent),
                null, "access_denied");

        assertEquals(ConsentOutcome.Kind.REFUSED, outcome.kind());
        assertTrue(outcome.reason().contains("refused"), "the publisher sees that the consent was refused");
        assertTrue(world.store.allChannels().isEmpty(), "no channel is created when consent is refused");
        assertTrue(world.audit.entries().isEmpty(), "nothing was changed, so nothing is audited");
    }

    @Test
    void unknownStateIsRefusedAndCreatesNothing() throws Exception {
        ChannelFixture world = new ChannelFixture();
        world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.completeConsent(ChannelFixture.ACTOR, "forged-state", "auth-code", null));

        assertEquals(400, failure.status(), "a state that names no consent is a state mismatch");
        assertTrue(world.store.allChannels().isEmpty());
    }

    @Test
    void stateCanBeUsedOnlyOnce() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        String state = ChannelFixture.stateOf(consent);
        answerTokenAndIdentity(world, READONLY, "UCone", "One");
        world.service.completeConsent(ChannelFixture.ACTOR, state, "auth-code", null);

        assertEquals(400, assertThrows(ProblemException.class,
                () -> world.service.completeConsent(ChannelFixture.ACTOR, state, "auth-code", null)).status(),
                "a replayed callback is refused");
    }

    @Test
    void expiredConsentIsRefused() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        world.clock.advance(Duration.ofMinutes(11));

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "code", null));

        assertEquals(400, failure.status(), "a consent older than its lifetime is not accepted");
    }

    @Test
    void consentStartedByAnotherPersonIsRefused() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        answerTokenAndIdentity(world, READONLY, "UCx", "X");
        Actor other = new Actor(ChannelFixture.OTHER_PERSON, "web", "req-2");

        assertEquals(400, assertThrows(ProblemException.class,
                () -> world.service.completeConsent(other, ChannelFixture.stateOf(consent), "code", null)).status(),
                "a callback carried by another person's session is a state mismatch");
        assertTrue(world.store.allChannels().isEmpty());
    }

    @Test
    void connectingAnAccountThatIsAlreadyConnectedReturnsTheExistingChannel() throws Exception {
        ChannelFixture world = new ChannelFixture();
        ConsentOutcome first = connect(world, READONLY, "UCsame", "Same");
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));

        ConsentOutcome second = connect(world, READONLY, "UCsame", "Same");

        assertEquals(ConsentOutcome.Kind.ALREADY_CONNECTED, second.kind());
        assertTrue(world.http.last("/revoke").isPresent(), "the unused new grant is revoked at the platform");
        assertEquals(first.channelId(), second.channelId());
        assertEquals(1, world.store.allChannels().size(), "one platform account is one channel");
    }

    @Test
    void reconnectKeepsChannelIdentityAndReplacesOnlyTheGrant() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channelId = connect(world, READONLY + " " + UPLOAD, "UCkeep", "Keep").channelId();
        world.store.updateChannel(null, needsReconnection(world, channelId));
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), channelId);
        answerTokenAndIdentity(world, READONLY + " " + UPLOAD, "UCkeep", "Keep renamed");

        ConsentOutcome outcome = world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "code", null);

        assertEquals(ConsentOutcome.Kind.CONNECTED, outcome.kind());
        assertEquals(channelId, outcome.channelId(), "the channel identity is unchanged");
        Channel channel = world.store.storedChannel(channelId).orElseThrow();
        assertEquals(ConnectionState.CONNECTED, channel.state(), "the status returns to connected");
        assertEquals("ya29.access", world.store.storedGrant(channelId).orElseThrow().accessToken(),
                "the grant was replaced with the new one");
        assertEquals("channel.reconnect", world.audit.entries().get(world.audit.entries().size() - 1).action());
    }

    @Test
    void reconnectWithADifferentAccountIsRefusedAndTheOldGrantStays() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channelId = connect(world, READONLY, "UCold", "Old").channelId();
        world.store.updateChannel(null, needsReconnection(world, channelId));
        String oldToken = world.store.storedGrant(channelId).orElseThrow().accessToken();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of(), channelId);
        answerTokenAndIdentity(world, READONLY, "UCother", "Other account");
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));

        ConsentOutcome outcome = world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "code", null);

        assertEquals(ConsentOutcome.Kind.ACCOUNT_MISMATCH, outcome.kind(),
                "the publisher is asked to connect a new channel instead");
        assertTrue(world.http.last("/revoke").isPresent(), "the grant of the other account is not kept");
        assertEquals(ConnectionState.NEEDS_RECONNECTION, world.store.storedChannel(channelId).orElseThrow().state(),
                "the channel is untouched");
        assertEquals(oldToken, world.store.storedGrant(channelId).orElseThrow().accessToken(), "the old grant stays");
    }

    @Test
    void missingRequestedScopeIsFlaggedOnTheChannelAndNotRefused() throws Exception {
        ChannelFixture world = new ChannelFixture();
        UUID channelId = connect(world, READONLY, "UCflag", "Flag").channelId();
        // The channel was enabled for publishing, but the grant carries only the account scope.
        Channel published = new Channel(channelId, "youtube", "UCflag", "Flag", ConnectionState.CONNECTED, null,
                Set.of("publish"), Set.of(READONLY), null, ChannelFixture.NOW, ChannelFixture.NOW, null);
        world.store.updateChannel(null, published);

        ChannelSnapshot snapshot = world.service.snapshot(channelId);

        assertEquals(Set.of(UPLOAD), snapshot.missingScopes(), "the missing scope for an enabled feature is flagged");
        assertEquals(ConnectionState.CONNECTED, snapshot.channel().state(), "a missing scope does not disconnect");
    }

    @Test
    void platformRejectingTheCodeRefusesWithoutCreatingAChannel() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(400, "{\"error\":\"invalid_grant\"}"));

        ConsentOutcome outcome = world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "bad", null);

        assertEquals(ConsentOutcome.Kind.REFUSED, outcome.kind());
        assertTrue(world.store.allChannels().isEmpty());
    }

    @Test
    void platformOutageDuringTheExchangeAnswers502AndCreatesNothing() throws Exception {
        ChannelFixture world = new ChannelFixture();
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(503, ""));

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "code", null));

        assertEquals(502, failure.status(), "an outage at the platform is reported as a bad gateway, retry later");
        assertTrue(world.store.allChannels().isEmpty());
    }

    @Test
    void consentUrlNamesOnlyTheRequestedFeaturesScopes() throws Exception {
        ChannelFixture world = new ChannelFixture();

        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of(), null);

        assertNotNull(ChannelFixture.stateOf(consent));
        assertFalse(consent.getRawQuery().contains("youtube.upload"),
                "a connection without the publish feature never asks for the upload scope");
    }

    @Test
    void featureNotDeclaredByTheAdapterIsRefusedAtStart() throws Exception {
        ChannelFixture world = new ChannelFixture();

        assertEquals(400, assertThrows(ProblemException.class,
                () -> world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("chapters"), null)).status());
    }

    @Test
    void unknownPlatformIsRefusedAtStart() throws Exception {
        ChannelFixture world = new ChannelFixture();

        assertEquals(400, assertThrows(ProblemException.class,
                () -> world.service.startConsent(ChannelFixture.ACTOR, "myspace", Set.of(), null)).status());
    }

    private static ConsentOutcome connect(ChannelFixture world, String scope, String account, String title) throws Exception {
        URI consent = world.service.startConsent(ChannelFixture.ACTOR, "youtube", Set.of("publish"), null);
        answerTokenAndIdentity(world, scope, account, title);
        return world.service.completeConsent(ChannelFixture.ACTOR, ChannelFixture.stateOf(consent), "code", null);
    }

    private static void answerTokenAndIdentity(ChannelFixture world, String scope, String account, String title) {
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.access", "1//refresh", 3600, scope)));
        world.http.route("GET", "/youtube/v3/channels", new ScriptedPlatform.Reply(200, ChannelFixture.channelJson(account, title)));
    }

    private static Channel needsReconnection(ChannelFixture world, UUID channelId) {
        return world.store.storedChannel(channelId).orElseThrow().needsReconnection("refresh token rejected", ChannelFixture.NOW);
    }
}
