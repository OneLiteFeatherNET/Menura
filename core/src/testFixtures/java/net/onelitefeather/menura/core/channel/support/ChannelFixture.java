package net.onelitefeather.menura.core.channel.support;

import net.onelitefeather.menura.core.channel.Actor;
import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.youtube.YouTubeAdapter;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.secret.SecretGenerator;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One fresh world per test: an injected clock, deterministic randomness, an in-memory store, a scripted Google token
 * endpoint and the YouTube adapter wired to them. No test shares any of it with another.
 */
public final class ChannelFixture {

    public static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    public static final UUID PERSON = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");
    public static final UUID OTHER_PERSON = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    public static final Actor ACTOR = new Actor(PERSON, "web", "req-1");
    public static final String YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly";
    public static final String YOUTUBE_UPLOAD = "https://www.googleapis.com/auth/youtube.upload";
    public static final String REDIRECT = "https://api-menura.example.test/channel-connections/callback";

    public final MutableClock clock = new MutableClock(NOW);
    public final InMemoryChannelStore store = new InMemoryChannelStore();
    public final RecordingAudit audit = new RecordingAudit();
    public final ScriptedPlatform http = new ScriptedPlatform();
    public final YouTubeAdapter youtube;
    public final ConnectionService service;

    public ChannelFixture() {
        this(new YouTubeAdapter.Settings("client-id-test", "client-secret-test", REDIRECT));
    }

    public ChannelFixture(YouTubeAdapter.Settings settings) {
        this.youtube = new YouTubeAdapter(clock, settings, http);
        this.service = service(store, new PlatformRegistry(List.of(youtube)));
    }

    /** A service over the given store and registry, with the same clock and randomness as this fixture. */
    public ConnectionService service(ChannelStore channelStore, PlatformRegistry registry) {
        DeterministicRandom random = new DeterministicRandom(42L);
        return new ConnectionService(new DirectTransactions(), clock, new IdentifierGenerator(random),
                new SecretGenerator(random), channelStore, audit, registry, Duration.ofMinutes(10), 0.8);
    }

    /** The state parameter of a consent URL, which the platform returns on the callback. */
    public static String stateOf(URI consentUri) {
        for (String pair : consentUri.getRawQuery().split("&")) {
            if (pair.startsWith("state=")) {
                return java.net.URLDecoder.decode(pair.substring("state=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("the consent URL carries no state: " + consentUri);
    }

    /** A token endpoint answer with the given tokens, in the shape Google returns. */
    public static String tokenJson(String accessToken, String refreshToken, long expiresIn, String scope) {
        String refresh = refreshToken == null ? "" : ",\"refresh_token\":\"" + refreshToken + "\"";
        return "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn + refresh
                + ",\"scope\":\"" + scope + "\",\"token_type\":\"Bearer\"}";
    }

    /** The channel list answer of the YouTube Data API for one channel. */
    public static String channelJson(String id, String title) {
        return "{\"items\":[{\"id\":\"" + id + "\",\"snippet\":{\"title\":\"" + title + "\"}}]}";
    }

    /**
     * Stores a connected YouTube channel with a grant, as a finished consent would have. Returns its id.
     */
    public UUID seedConnected(String account, Instant accessExpiresAt, String refreshToken) {
        UUID id = UUID.nameUUIDFromBytes(("channel-" + account).getBytes(StandardCharsets.UTF_8));
        store.insertChannel(null, new Channel(id, "youtube", account, "Channel " + account, ConnectionState.CONNECTED,
                null, Set.of("publish"), Set.of(YOUTUBE_READONLY, YOUTUBE_UPLOAD), null, NOW, NOW, null));
        store.saveGrant(null, new ChannelGrant(id, "ya29.old", refreshToken, accessExpiresAt, NOW));
        return id;
    }

    /** The access token the stored grant of a channel holds right now, for asserting a refresh replaced it. */
    public String storedAccessToken(UUID channelId) {
        return store.storedGrant(channelId).orElseThrow().accessToken();
    }

    public static Map<String, String> none() {
        return Map.of();
    }
}
