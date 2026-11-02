package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.jwk.RSAKey;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.audit.Channel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign-in through Menura's authorization server against the in-process fake of Entra ID. The browser side is played
 * by the test: it follows the redirect to the provider, then calls the callback with the code the provider returned.
 */
@MicronautTest(rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "menura.test.in-memory-auth", value = "true")
class BffSignInTest implements TestPropertyProvider {

    static final String VIEWER_GROUP = "11111111-1111-4111-8111-111111111111";
    static final String EDITOR_GROUP = "22222222-2222-4222-8222-222222222222";
    static final String UNMAPPED_GROUP = "99999999-9999-4999-8999-999999999999";
    static final String WEB_APP = "https://menura.onelitefeather.net/";
    static final String COOKIE = "__Host-menura-session";

    static final String ISSUER = "https://api-menura.onelitefeather.net";
    static final UUID API_PERSON = UUID.fromString("0f8c3a52-7d1e-4b6a-9c2f-5e8d1a3b4c60");

    private FakeEntraProvider entra;
    private RSAKey menuraKey;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    TestClock clock;

    @Inject
    InMemoryAuthStore store;

    @Inject
    MenuraTokenIssuer issuer;

    @Override
    public Map<String, String> getProperties() {
        try {
            entra = new FakeEntraProvider();
        } catch (IOException e) {
            throw new IllegalStateException("the loopback provider must start", e);
        }
        return Map.of(
                // The browser follows redirects, the test client must not: the callback's redirect targets the web host.
                "micronaut.http.client.follow-redirects", "false",
                "menura.auth.entra.issuer", entra.issuer(),
                "menura.auth.entra.client-id", FakeEntraProvider.CLIENT_ID,
                "menura.auth.entra.client-secret", FakeEntraProvider.CLIENT_SECRET,
                "menura.auth.entra.redirect-uri", "https://api-menura.onelitefeather.net/auth/callback",
                "menura.auth.web-app-url", WEB_APP,
                "menura.auth.role-groups.viewer", VIEWER_GROUP,
                "menura.auth.role-groups.editor", EDITOR_GROUP,
                "menura.auth.signing-key", menuraKey().toJSONString());
    }

    /** Menura's signing key of this test class, created once per class (the instance lives for the whole class). */
    private RSAKey menuraKey() {
        if (menuraKey == null) {
            menuraKey = TestKeys.newRsaKey("menura-bff-test");
        }
        return menuraKey;
    }

    @AfterAll
    void stopProvider() {
        if (entra != null) {
            entra.close();
        }
    }

    @BeforeEach
    void freshState() {
        store.clear();
        clock.reset();
        entra.echoWrongNonce(null);
    }

    @Test
    void anonymousCallerOfTheSessionEndpointIsRefusedWith401AsProblemDetails() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/auth/session"), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void loginRedirectsToTheProviderWithStateNonceAndS256Challenge() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/auth/login"), String.class);

        assertEquals(HttpStatus.FOUND, response.getStatus());
        URI location = URI.create(response.header("Location"));
        Map<String, String> query = query(location);
        assertTrue(location.toString().startsWith(entra.issuer().replace("/tenant/v2.0", "")
                + "/tenant/oauth2/v2.0/authorize"), "the redirect must go to the upstream authorization endpoint");
        assertEquals("code", query.get("response_type"));
        assertEquals(FakeEntraProvider.CLIENT_ID, query.get("client_id"));
        assertEquals("S256", query.get("code_challenge_method"));
        assertNotNull(query.get("state"));
        assertNotNull(query.get("nonce"));
        assertNotNull(query.get("code_challenge"));
        assertTrue(response.getHeaders().getAll("Set-Cookie").isEmpty(),
                "the login step must not hand the browser any session");
    }

    @Test
    void viewerSignInSetsOnlyAHostOnlySessionCookieWithTheRequiredAttributes() {
        String setCookie = signIn("viewer-1", List.of(VIEWER_GROUP)).setCookie();

        assertTrue(setCookie.startsWith(COOKIE + "="), "the session cookie carries the __Host- prefix");
        assertTrue(setCookie.contains("; Path=/"), "__Host- cookies must use Path=/");
        assertTrue(setCookie.contains("; Secure"), "the session cookie must be Secure");
        assertTrue(setCookie.contains("; HttpOnly"), "the session cookie must be HttpOnly");
        assertTrue(setCookie.contains("; SameSite=Strict"), "the session cookie must be SameSite=Strict");
        assertFalse(setCookie.contains("Domain="), "the session cookie must be host-only");
    }

    @Test
    void theBrowserReceivesNoTokenInTheCallbackResponse() {
        SignIn signIn = signIn("viewer-1", List.of(VIEWER_GROUP));

        assertFalse(signIn.setCookie().substring(COOKIE.length()).contains("eyJ"),
                "the cookie is an opaque secret, not a JWT");
        assertFalse(signIn.callbackBody().contains("eyJ"), "no access or refresh token may appear in the body");
        assertFalse(signIn.callbackBody().toLowerCase().contains("refresh_token"));
    }

    @Test
    void signedInPersonSeesTheirRolesThroughTheSessionCookie() {
        String cookie = signIn("viewer-1", List.of(VIEWER_GROUP)).cookieValue();

        Map<?, ?> session = client.toBlocking().retrieve(
                HttpRequest.GET("/auth/session").header("Cookie", COOKIE + "=" + cookie), Map.class);

        assertEquals("viewer-1", session.get("subject"));
        assertEquals(List.of("VIEWER"), session.get("roles"));
    }

    @Test
    void personInSeveralMappedGroupsReceivesExactlyTheMappedRoles() {
        String cookie = signIn("both-1", List.of(VIEWER_GROUP, EDITOR_GROUP)).cookieValue();

        Map<?, ?> session = client.toBlocking().retrieve(
                HttpRequest.GET("/auth/session").header("Cookie", COOKIE + "=" + cookie), Map.class);

        assertEquals(List.of("EDITOR", "VIEWER"), session.get("roles"),
                "the session carries the roles of both groups and nothing else");
    }

    @Test
    void menuraBearerTokenAuthenticatesTheCallerWithItsRoles() {
        String token = issuer.issue("api-caller-1", API_PERSON, Set.of(Role.EDITOR), Channel.MCP).token();

        Map<?, ?> session = client.toBlocking().retrieve(
                HttpRequest.GET("/auth/session").header("Authorization", "Bearer " + token), Map.class);

        assertEquals("api-caller-1", session.get("subject"));
        assertEquals(List.of("EDITOR"), session.get("roles"));
    }

    @Test
    void bearerTokenForAnotherAudienceLeavesTheCallerAnonymous() {
        // Signed by Menura's own key, so only the audience is wrong.
        String foreign = TestKeys.sign(menuraKey, TestKeys.claims(ISSUER, "https://other.example",
                clock.instant().minusSeconds(60), clock.instant().plusSeconds(600)).build());

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/auth/session")
                        .header("Authorization", "Bearer " + foreign), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus(), "a token for another audience authorises nothing");
    }

    @Test
    void storedSessionHoldsOnlyTheDigestOfTheCookieSecret() {
        String cookie = signIn("viewer-1", List.of(VIEWER_GROUP)).cookieValue();

        assertEquals(1, store.sessionCount(), "one sign-in leaves exactly one session");
        assertTrue(store.hasSessionFor(SecretHasher.hash(cookie)), "the store finds the session by the digest of the secret");
        assertFalse(store.hasSessionFor(cookie.getBytes(StandardCharsets.UTF_8)),
                "the raw cookie value is never a stored key");
    }

    @Test
    void personWithoutAMappedGroupIsRefusedWith403AndGetsNoSession() {
        entra.approveNextSignInAs("outsider-1", List.of(UNMAPPED_GROUP));

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> callbackAfterApproval());

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus(), "no Menura role means no access");
        assertEquals(0, store.sessionCount());
    }

    @Test
    void callbackWithAnUnknownStateIsRefusedWith400() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/auth/callback?code=abc&state=forged"),
                        Map.class));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
    }

    @Test
    void signInCannotBeReplayed() {
        entra.approveNextSignInAs("viewer-1", List.of(VIEWER_GROUP));
        String callbackUri = callbackUriAfterApproval();
        client.toBlocking().exchange(HttpRequest.GET(callbackUri), String.class);

        HttpClientResponseException replay = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET(callbackUri), String.class));

        assertEquals(HttpStatus.BAD_REQUEST, replay.getStatus(), "a used sign-in transaction must not work again");
    }

    @Test
    void idTokenWithAWrongNonceIsRefusedWith401AndGetsNoSession() {
        entra.approveNextSignInAs("viewer-1", List.of(VIEWER_GROUP));
        entra.echoWrongNonce("not-the-nonce-that-was-sent");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> callbackAfterApproval());

        assertEquals(HttpStatus.UNAUTHORIZED, refused.getStatus());
        assertEquals(0, store.sessionCount());
    }

    @Test
    void logoutEndsTheSessionOnTheServer() {
        String cookie = signIn("viewer-1", List.of(VIEWER_GROUP)).cookieValue();

        var logout = client.toBlocking().exchange(HttpRequest.POST("/auth/logout", "")
                .header("Cookie", COOKIE + "=" + cookie), String.class);

        assertEquals(HttpStatus.NO_CONTENT, logout.getStatus());
        assertTrue(logout.getHeaders().get("Set-Cookie").contains("Max-Age=0"), "the browser cookie is cleared");
        assertEquals(0, store.sessionCount(), "the server forgets the session as well");
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
                HttpRequest.GET("/auth/session").header("Cookie", COOKIE + "=" + cookie), Map.class),
                "the old cookie no longer opens a session");
    }

    @Test
    void sessionEndsWhenItsLifetimeIsOver() {
        String cookie = signIn("viewer-1", List.of(VIEWER_GROUP)).cookieValue();
        clock.advance(Duration.ofHours(8).plusSeconds(1));

        HttpClientResponseException expired = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/auth/session")
                        .header("Cookie", COOKIE + "=" + cookie), Map.class));

        assertEquals(HttpStatus.UNAUTHORIZED, expired.getStatus());
    }

    @Test
    void sessionHoldsThePersonAndRolesOfTheSignInAndNoToken() {
        String cookie = signIn("viewer-1", List.of(VIEWER_GROUP)).cookieValue();

        AuthStore.Session session = store.storedSession(SecretHasher.hash(cookie)).orElseThrow();

        assertNotNull(session.personId(), "the session names Menura's person");
        assertEquals(Set.of(Role.VIEWER), session.roles());
        assertEquals("viewer-1", session.subject());
    }

    @Test
    void signingInAgainAsTheSamePersonKeepsThePersonId() {
        String first = signIn("returning-1", List.of(VIEWER_GROUP)).cookieValue();
        UUID firstPerson = store.storedSession(SecretHasher.hash(first)).orElseThrow().personId();

        String second = signIn("returning-1", List.of(VIEWER_GROUP)).cookieValue();
        UUID secondPerson = store.storedSession(SecretHasher.hash(second)).orElseThrow().personId();

        assertEquals(firstPerson, secondPerson, "one Entra ID identity is one person in Menura");
    }

    // ---- browser steps -------------------------------------------------------------------------------------------

    private record SignIn(String cookieValue, String setCookie, String callbackBody) {
    }

    /** Runs login, the provider's approval and the callback, the way a browser does. */
    private SignIn signIn(String subject, List<String> groups) {
        entra.approveNextSignInAs(subject, groups);
        var callback = callback();
        String setCookie = callback.getHeaders().get("Set-Cookie");
        String value = setCookie.substring((COOKIE + "=").length(), setCookie.indexOf(';'));
        return new SignIn(value, setCookie, callback.getBody(String.class).orElse(""));
    }

    private io.micronaut.http.HttpResponse<?> callback() {
        return client.toBlocking().exchange(HttpRequest.GET(callbackUriAfterApproval()), String.class);
    }

    private void callbackAfterApproval() {
        client.toBlocking().exchange(HttpRequest.GET(callbackUriAfterApproval()), String.class);
    }

    /** Starts a sign-in, lets the fake provider approve it and returns the callback path it redirects to. */
    private String callbackUriAfterApproval() {
        var login = client.toBlocking().exchange(HttpRequest.GET("/auth/login"), String.class);
        URI toProvider = URI.create(login.header("Location"));
        try {
            java.net.http.HttpResponse<Void> approved = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()
                    .send(java.net.http.HttpRequest.newBuilder(toProvider).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
            URI redirectToCallback = URI.create(approved.headers().firstValue("Location").orElseThrow());
            return "/auth/callback?" + redirectToCallback.getRawQuery();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("the fake provider must answer the approval", e);
        }
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new java.util.HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return values;
    }
}
