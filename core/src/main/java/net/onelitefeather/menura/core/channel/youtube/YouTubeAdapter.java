package net.onelitefeather.menura.core.channel.youtube;

import com.nimbusds.jose.util.JSONObjectUtils;
import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformFeature;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The YouTube adapter, connection part: Google OAuth consent with PKCE, token exchange, refresh with rotation,
 * revocation and the identity of the channel. Publishing, status, deletion, listing and metrics belong to the YouTube
 * integration and insights changes; until they land those operations answer {@link Unsupported} and the capabilities
 * say so.
 *
 * <p>Every request goes to a Google host from {@link #HOSTS} over HTTPS. Tokens and client secrets are sent in the
 * request only and never placed in an error message.
 */
public final class YouTubeAdapter implements PlatformAdapter {

    public static final String PLATFORM = "youtube";

    /** The videos.list call takes at most 50 video identifiers; one metrics read batches up to that many. */
    static final int YOUTUBE_BATCH_SIZE = 50;
    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    static final String REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    static final String CHANNELS_ENDPOINT = "https://www.googleapis.com/youtube/v3/channels";
    static final Set<String> HOSTS = Set.of("accounts.google.com", "oauth2.googleapis.com", "www.googleapis.com");

    static final String READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
    static final String UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.upload";
    static final String PUBLISH_FEATURE = "publish";

    /** The app's client settings. The secret is redacted from the text form. */
    public record Settings(String clientId, String clientSecret, String redirectUri) {

        public Settings {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(clientSecret, "clientSecret");
            Objects.requireNonNull(redirectUri, "redirectUri");
        }

        @Override
        public String toString() {
            return "Settings[clientId=" + clientId + ", clientSecret=redacted, redirectUri=" + redirectUri + "]";
        }
    }

    private final Clock clock;
    private final Settings settings;
    private final HttpTransport http;

    public YouTubeAdapter(Clock clock, Settings settings, HttpTransport http) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public String redirectUri() {
        return settings.redirectUri();
    }

    @Override
    public PlatformCapabilities capabilities() {
        return new PlatformCapabilities(false, false, false, false, true);
    }

    @Override
    public List<PlatformFeature> features() {
        return List.of(
                new PlatformFeature(ACCOUNT_FEATURE, Set.of(READONLY_SCOPE)),
                new PlatformFeature(PUBLISH_FEATURE, Set.of(UPLOAD_SCOPE)));
    }

    @Override
    public URI authorizationUri(ConsentRequest request) {
        requireConfigured();
        String scope = request.scopes().stream().sorted().collect(Collectors.joining(" "));
        return URI.create(AUTHORIZATION_ENDPOINT + "?" + form(
                "response_type", "code",
                "client_id", settings.clientId(),
                "redirect_uri", request.redirectUri(),
                "scope", scope,
                "state", request.state(),
                "code_challenge", request.codeChallenge(),
                "code_challenge_method", "S256",
                "access_type", "offline",
                "prompt", "consent"));
    }

    @Override
    public TokenSet exchangeCode(String code, String codeVerifier, String redirectUri) {
        requireConfigured();
        String body = form(
                "grant_type", "authorization_code",
                "code", code,
                "code_verifier", codeVerifier,
                "redirect_uri", redirectUri,
                "client_id", settings.clientId(),
                "client_secret", settings.clientSecret());
        return tokensOf(send("POST", TOKEN_ENDPOINT, body, null, "the token exchange"));
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        requireConfigured();
        String body = form(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "client_id", settings.clientId(),
                "client_secret", settings.clientSecret());
        return tokensOf(send("POST", TOKEN_ENDPOINT, body, null, "the token refresh"));
    }

    @Override
    public void revoke(String token) {
        HttpTransport.Reply reply = post(REVOCATION_ENDPOINT, form("token", token), null, "the revocation");
        if (reply.status() / 100 == 2 || (reply.status() == 400 && reply.body().contains("invalid_token"))) {
            return;
        }
        throw failureOf(reply, "the revocation");
    }

    @Override
    public Optional<AccountIdentity> identify(String accessToken) {
        Map<String, Object> document = parse(send("GET", CHANNELS_ENDPOINT + "?part=snippet&mine=true", null, accessToken,
                "the channel lookup"));
        Object items = document.get("items");
        if (!(items instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> channel)) {
            return Optional.empty();
        }
        Object id = channel.get("id");
        Object snippet = channel.get("snippet");
        String title = snippet instanceof Map<?, ?> s && s.get("title") instanceof String value ? value : "";
        if (!(id instanceof String identifier) || identifier.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AccountIdentity(identifier, title.isBlank() ? identifier : title));
    }

    @Override
    public PublishResult publish(String accessToken, PublishRequest request) {
        throw new Unsupported("publishing to YouTube");
    }

    @Override
    public PublicationStatus status(String accessToken, String platformItemId) {
        throw new Unsupported("the status of a YouTube publication");
    }

    @Override
    public void delete(String accessToken, String platformItemId) {
        throw new Unsupported("deleting a YouTube video");
    }

    @Override
    public List<PublishedItem> listPublished(String accessToken, int limit) {
        throw new Unsupported("listing YouTube videos");
    }

    @Override
    public int metricsBatchSize() {
        return YOUTUBE_BATCH_SIZE;
    }

    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                     Set<String> metrics) {
        throw new Unsupported("reading YouTube metrics");
    }

    // ------------------------------------------------------------ helpers

    private void requireConfigured() {
        if (settings.clientId().isBlank() || settings.clientSecret().isBlank()) {
            throw new ProblemException(503, "Service Unavailable", "The YouTube app is not configured.");
        }
    }

    private HttpTransport.Reply post(String endpoint, String body, String bearer, String what) {
        return exchange("POST", endpoint, bearer, body, what);
    }

    /** Sends a request and returns the body of a 2xx answer; anything else becomes a classified failure. */
    private String send(String method, String endpoint, String body, String bearer, String what) {
        HttpTransport.Reply reply = exchange(method, endpoint, bearer, body, what);
        if (reply.status() / 100 != 2) {
            throw failureOf(reply, what);
        }
        return reply.body();
    }

    private HttpTransport.Reply exchange(String method, String endpoint, String bearer, String body, String what) {
        URI uri = URI.create(endpoint);
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
            throw new IllegalStateException("the YouTube adapter may only reach its Google endpoints");
        }
        try {
            return http.send(method, uri, bearer, body);
        } catch (IOException failure) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE,
                    "YouTube could not be reached for " + what + ".", null);
        }
    }

    private static PlatformException failureOf(HttpTransport.Reply reply, String what) {
        String body = reply.body() == null ? "" : reply.body();
        if (reply.status() == 429 || (reply.status() == 403 && (body.contains("quotaExceeded")
                || body.contains("rateLimitExceeded")))) {
            return new PlatformException(PlatformException.Kind.RATE_LIMITED,
                    "YouTube is rate limiting " + what + ".", retryAfter(reply));
        }
        if (reply.status() == 400 || reply.status() == 401) {
            return new PlatformException(PlatformException.Kind.REJECTED,
                    "YouTube rejected " + what + ".", null);
        }
        return new PlatformException(PlatformException.Kind.UNAVAILABLE,
                "YouTube failed " + what + " with status " + reply.status() + ".", null);
    }

    private static Duration retryAfter(HttpTransport.Reply reply) {
        return reply.retryAfter()
                .flatMap(value -> {
                    try {
                        return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
                    } catch (NumberFormatException notSeconds) {
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }

    private TokenSet tokensOf(String body) {
        Map<String, Object> document = parse(body);
        if (!(document.get("access_token") instanceof String access) || access.isBlank()) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE,
                    "YouTube answered without an access token.", null);
        }
        Instant expiresAt = document.get("expires_in") instanceof Number seconds
                ? clock.instant().plusSeconds(seconds.longValue()) : null;
        String refresh = document.get("refresh_token") instanceof String value && !value.isBlank() ? value : null;
        Set<String> scopes = document.get("scope") instanceof String granted && !granted.isBlank()
                ? Set.of(granted.trim().split("\\s+")) : Set.of();
        return new TokenSet(access, refresh, expiresAt, scopes);
    }

    private static Map<String, Object> parse(String json) {
        try {
            return JSONObjectUtils.parse(json);
        } catch (ParseException notJson) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE,
                    "YouTube answered with a body that is not JSON.", null);
        }
    }

    private static String form(String... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(pairs[i + 1] == null ? "" : pairs[i + 1], StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
