package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.ScriptedPlatform;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeAdapterTest {

    private static final String READONLY = ChannelFixture.YOUTUBE_READONLY;
    private static final String UPLOAD = ChannelFixture.YOUTUBE_UPLOAD;

    @Test
    void consentUrlCarriesStateChallengeOfflineAccessAndTheRegisteredRedirect() {
        ChannelFixture world = new ChannelFixture();

        Map<String, String> query = query(world.youtube.authorizationUri(new PlatformAdapter.ConsentRequest(
                "state-abc", "challenge-xyz", ChannelFixture.REDIRECT, Set.of(READONLY, UPLOAD))));

        assertEquals("state-abc", query.get("state"), "the state of the transaction is passed through");
        assertEquals("challenge-xyz", query.get("code_challenge"), "the PKCE challenge is sent with the consent");
        assertEquals("S256", query.get("code_challenge_method"), "the challenge uses the S256 method");
        assertEquals("offline", query.get("access_type"), "a refresh token is requested for later refreshes");
        assertEquals("consent", query.get("prompt"), "the consent screen is shown so a refresh token is issued again");
        assertEquals(ChannelFixture.REDIRECT, query.get("redirect_uri"));
        assertEquals("client-id-test", query.get("client_id"));
    }

    @Test
    void consentUrlRequestsExactlyTheScopesOfTheEnabledFeatures() {
        ChannelFixture world = new ChannelFixture();

        assertEquals(Set.of(READONLY), world.youtube.scopesFor(Set.of()),
                "without an enabled feature only the account scope is requested");
        assertEquals(Set.of(READONLY, UPLOAD), world.youtube.scopesFor(Set.of("publish")),
                "enabling publishing adds the upload scope and nothing more");
        assertEquals(Set.of(READONLY), world.youtube.scopesFor(Set.of("chapters")),
                "a feature the adapter does not declare requests no scope");
    }

    @Test
    void consentUrlScopeParameterEqualsTheDeclaredScopesOfTheEnabledFeatures() {
        ChannelFixture world = new ChannelFixture();
        Set<String> requested = world.youtube.scopesFor(Set.of("publish"));

        String scope = query(world.youtube.authorizationUri(new PlatformAdapter.ConsentRequest(
                "s", "c", ChannelFixture.REDIRECT, requested))).get("scope");

        assertEquals(requested, Set.of(scope.split(" ")), "the scope sent to the platform is the adapter's declaration");
    }

    @Test
    void tokenExchangeSendsTheCodeVerifierAndReturnsTokensWithExpiry() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.access", "1//refresh", 3600, READONLY + " " + UPLOAD)));

        PlatformAdapter.TokenSet tokens = world.youtube.exchangeCode("auth-code", "verifier-123", ChannelFixture.REDIRECT);

        assertEquals("ya29.access", tokens.accessToken());
        assertEquals("1//refresh", tokens.refreshToken());
        assertEquals(ChannelFixture.NOW.plusSeconds(3600), tokens.accessExpiresAt(),
                "the expiry is the injected clock plus expires_in");
        assertEquals(Set.of(READONLY, UPLOAD), tokens.scopes());
        String form = world.http.last("/token").orElseThrow().formBody();
        assertTrue(form.contains("code_verifier=verifier-123"), "the verifier proves the consent to the platform");
        assertTrue(form.contains("client_secret=client-secret-test"), "the client secret is sent in the form only");
    }

    @Test
    void refreshWithoutARotatedTokenReturnsNoRefreshToken() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.new", null, 3600, READONLY)));

        assertNull(world.youtube.refresh("1//old").refreshToken(), "no rotation means the old refresh token stays in use");
    }

    @Test
    void refreshRefusedAsInvalidGrantIsRejectedAndTheMessageHoldsNoToken() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"));

        PlatformException failure = assertThrows(PlatformException.class, () -> world.youtube.refresh("1//SECRET-REFRESH"));

        assertEquals(PlatformException.Kind.REJECTED, failure.kind(), "an invalid grant means reconnection is needed");
        assertFalse(failure.getMessage().contains("SECRET-REFRESH"), "the token never reaches the error message");
        assertFalse(failure.getMessage().contains("expired"), "the platform's body never reaches the error message");
    }

    @Test
    void rateLimitedRefreshCarriesThePlatformRetryHint() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(429, "{}", java.util.Optional.of("120")));

        PlatformException failure = assertThrows(PlatformException.class, () -> world.youtube.refresh("1//r"));

        assertEquals(PlatformException.Kind.RATE_LIMITED, failure.kind());
        assertEquals(Duration.ofSeconds(120), failure.retryAfter(), "the Retry-After the platform sent is kept");
    }

    @Test
    void platformServerErrorIsUnavailableNotRejected() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(503, ""));

        PlatformException failure = assertThrows(PlatformException.class, () -> world.youtube.refresh("1//r"));

        assertEquals(PlatformException.Kind.UNAVAILABLE, failure.kind(),
                "a platform outage must not mark the grant as dead");
    }

    @Test
    void revokingAnAlreadyInvalidTokenIsNotAnError() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(400, "{\"error\":\"invalid_token\"}"));

        assertDoesNotThrow(() -> world.youtube.revoke("1//gone"),
                "the grant is already gone at the platform, which is what a revocation wants");
    }

    @Test
    void identifyReturnsTheChannelOfTheAccount() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("GET", "/youtube/v3/channels", new ScriptedPlatform.Reply(200,
                ChannelFixture.channelJson("UCabc", "Main channel")));

        PlatformAdapter.AccountIdentity identity = world.youtube.identify("ya29.a").orElseThrow();

        assertEquals("UCabc", identity.platformAccountId());
        assertEquals("Main channel", identity.displayName());
    }

    @Test
    void identifyIsEmptyForAnAccountWithoutAChannel() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("GET", "/youtube/v3/channels", new ScriptedPlatform.Reply(200, "{\"items\":[]}"));

        assertTrue(world.youtube.identify("ya29.a").isEmpty(), "an account without a channel is not a connection");
    }

    @Test
    void everyRequestGoesOnlyToGoogleHosts() {
        ChannelFixture world = new ChannelFixture();
        world.http.route("POST", "/token", new ScriptedPlatform.Reply(200,
                ChannelFixture.tokenJson("ya29.a", "1//r", 60, READONLY)));
        world.http.route("GET", "/youtube/v3/channels", new ScriptedPlatform.Reply(200, ChannelFixture.channelJson("UC1", "T")));
        world.http.route("POST", "/revoke", new ScriptedPlatform.Reply(200, "{}"));

        world.youtube.exchangeCode("c", "v", ChannelFixture.REDIRECT);
        world.youtube.identify("ya29.a");
        world.youtube.revoke("1//r");

        Set<String> hosts = world.http.sent().stream().map(sent -> sent.uri().getHost()).collect(Collectors.toSet());
        assertEquals(Set.of("oauth2.googleapis.com", "www.googleapis.com"), hosts,
                "the adapter contacts only the official Google endpoints of the connection");
    }

    @Test
    void consentIsRefusedWith503WhenTheAppIsNotConfigured() {
        ChannelFixture world = new ChannelFixture(new YouTubeAdapter.Settings("", "", ChannelFixture.REDIRECT));

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.youtube.authorizationUri(new PlatformAdapter.ConsentRequest("s", "c", ChannelFixture.REDIRECT, Set.of(READONLY))));

        assertEquals(503, failure.status(), "a missing client id answers 503 instead of starting a broken consent");
    }

    @Test
    void publishingIsDeclaredUnsupportedInThisBuildAndRevocationIsOffered() {
        ChannelFixture world = new ChannelFixture();

        assertFalse(world.youtube.capabilities().publishing(), "publishing lands in the YouTube integration change");
        assertFalse(world.youtube.capabilities().nativeScheduling(), "scheduling is enabled with publishing");
        assertTrue(world.youtube.capabilities().revocation(), "Google offers revocation, requested on disconnect");
        assertThrows(PlatformAdapter.Unsupported.class,
                () -> world.youtube.publish("ya29.a", new PlatformAdapter.PublishRequest("t", PlatformAdapter.Visibility.PRIVATE, null)));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        Arrays.stream(uri.getRawQuery().split("&")).forEach(pair -> {
            int eq = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        });
        return params;
    }
}
