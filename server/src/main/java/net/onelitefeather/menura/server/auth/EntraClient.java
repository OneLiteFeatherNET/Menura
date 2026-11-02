package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The upstream side of the authorization server: Entra ID as OpenID Connect provider. Menura is a confidential client
 * (authorization code with PKCE and a client secret). It validates the ID token itself and trusts nothing it did not
 * verify; the identity it returns is then turned into Menura's own token by the caller.
 */
@Singleton
public class EntraClient {

    private static final Logger LOG = LoggerFactory.getLogger(EntraClient.class);
    static final String SCOPE = "openid profile";
    static final Duration JWKS_LIFETIME = Duration.ofMinutes(10);

    private final AuthSettings settings;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private volatile Discovery discovery;
    private volatile CachedKeys keys;

    /** The identity an Entra ID sign-in proved: its stable subject and the group ids it carries. */
    public record UpstreamIdentity(String subject, List<String> groups) {
    }

    private record Discovery(String issuer, URI authorizationEndpoint, URI tokenEndpoint, URI jwksUri) {
    }

    private record CachedKeys(JWKSet set, Instant fetchedAt) {
    }

    public EntraClient(AuthSettings settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Whether the Entra ID application is configured. Without it, sign-in is answered with 503. */
    public boolean configured() {
        return !blank(settings.entraIssuer()) && !blank(settings.entraClientId()) && !blank(settings.entraClientSecret());
    }

    /** The URL the browser is sent to for the Entra ID sign-in of one transaction. */
    public URI authorizeUri(String state, String nonce, String codeChallenge) {
        requireConfigured();
        return URI.create(discovery().authorizationEndpoint() + "?" + form(
                "response_type", "code",
                "client_id", settings.entraClientId(),
                "redirect_uri", settings.entraRedirectUri(),
                "scope", SCOPE,
                "state", state,
                "nonce", nonce,
                "code_challenge", codeChallenge,
                "code_challenge_method", "S256"));
    }

    /**
     * Exchanges the authorization code for the ID token at the token endpoint, then verifies that token: signature
     * with the provider's keys, algorithm, issuer, audience, expiry and the nonce of this transaction.
     */
    public UpstreamIdentity exchange(String code, String codeVerifier, String expectedNonce) {
        requireConfigured();
        Discovery metadata = discovery();
        String body = form(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", settings.entraRedirectUri(),
                "client_id", settings.entraClientId(),
                "client_secret", settings.entraClientSecret(),
                "code_verifier", codeVerifier);
        String idToken = idTokenOf(post(metadata.tokenEndpoint(), body));

        TokenVerifier verifier = new TokenVerifier(clock, metadata.issuer(), settings.entraClientId(),
                Set.of(JWSAlgorithm.RS256), this::providerKeys);
        JWTClaimsSet claims = verifier.verify(idToken);
        if (!expectedNonce.equals(nonceOf(claims))) {
            throw new ProblemException(401, TokenVerifier.REFUSED_TITLE, TokenVerifier.REFUSED_DETAIL);
        }
        return new UpstreamIdentity(claims.getSubject(), groupsOf(claims));
    }

    private Discovery discovery() {
        Discovery cached = discovery;
        if (cached != null) {
            return cached;
        }
        String issuer = settings.entraIssuer();
        requireSecureUrl(issuer);
        String body = get(URI.create(issuer + "/.well-known/openid-configuration"));
        Map<String, Object> document = parseObject(body);
        if (!issuer.equals(document.get("issuer"))) {
            throw upstreamFailure("the provider metadata names another issuer");
        }
        Discovery found = new Discovery(issuer,
                URI.create(String.valueOf(document.get("authorization_endpoint"))),
                URI.create(String.valueOf(document.get("token_endpoint"))),
                URI.create(String.valueOf(document.get("jwks_uri"))));
        discovery = found;
        return found;
    }

    private JWKSet providerKeys() {
        CachedKeys cached = keys;
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.fetchedAt().plus(JWKS_LIFETIME))) {
            return cached.set();
        }
        try {
            JWKSet fetched = JWKSet.parse(get(discovery().jwksUri()));
            keys = new CachedKeys(fetched, now);
            return fetched;
        } catch (ParseException e) {
            throw upstreamFailure("the provider keys could not be read");
        }
    }

    private String idTokenOf(String tokenResponse) {
        Object idToken = parseObject(tokenResponse).get("id_token");
        if (!(idToken instanceof String token) || token.isBlank()) {
            throw upstreamFailure("the token response carries no ID token");
        }
        return token;
    }

    private static String nonceOf(JWTClaimsSet claims) {
        try {
            return claims.getStringClaim("nonce");
        } catch (ParseException e) {
            return null;
        }
    }

    private static List<String> groupsOf(JWTClaimsSet claims) {
        try {
            List<String> groups = claims.getStringListClaim("groups");
            return groups == null ? List.of() : groups;
        } catch (ParseException e) {
            return List.of();
        }
    }

    private String post(URI uri, String form) {
        return send(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), "the token request failed");
    }

    private String get(URI uri) {
        return send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                "the provider could not be reached");
    }

    private String send(HttpRequest request, String failure) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw upstreamFailure(failure + " with status " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw upstreamFailure(failure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw upstreamFailure(failure);
        }
    }

    private static Map<String, Object> parseObject(String json) {
        try {
            return JSONObjectUtils.parse(json);
        } catch (ParseException e) {
            throw upstreamFailure("a provider response is not a JSON object");
        }
    }

    /**
     * Provider failures are 502 with a fixed detail. The cause is logged on the server only: it names the failed step,
     * never a token, a code or a secret.
     */
    private static ProblemException upstreamFailure(String cause) {
        LOG.warn("Entra ID sign-in failed: {}", cause);
        return new ProblemException(502, "Bad Gateway", "Entra ID could not complete the sign-in.");
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new ProblemException(503, "Service Unavailable", "Sign-in with Entra ID is not configured.");
        }
    }

    /** Discovery must run over HTTPS, except on the loopback interface used by local tests and development. */
    private static void requireSecureUrl(String issuer) {
        URI uri = URI.create(issuer);
        boolean loopback = "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) {
            throw new ProblemException(503, "Service Unavailable", "Sign-in with Entra ID is not configured.");
        }
    }

    private static String form(String... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
