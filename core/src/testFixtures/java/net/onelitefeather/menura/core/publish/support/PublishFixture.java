package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.channel.support.DirectTransactions;
import net.onelitefeather.menura.core.channel.support.DeterministicRandom;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.channel.support.MutableClock;
import net.onelitefeather.menura.core.channel.support.RecordingAudit;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.support.InMemoryExternalPostStore;
import net.onelitefeather.menura.core.publish.Content;
import net.onelitefeather.menura.core.publish.PostActor;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.core.publish.FinishedAsset;
import net.onelitefeather.menura.core.publish.PublicationRunner;
import net.onelitefeather.menura.core.publish.QuotaBudget;
import net.onelitefeather.menura.core.publish.QuotaLedger;
import net.onelitefeather.menura.core.secret.TokenCipher;
import net.onelitefeather.menura.core.publish.PublishSettings;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.series.Audience;
import net.onelitefeather.menura.core.series.SeriesDraft;
import net.onelitefeather.menura.core.series.SeriesService;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.series.support.FixedBrandTokens;
import net.onelitefeather.menura.core.series.support.InMemorySeriesStore;
import net.onelitefeather.menura.core.auth.Role;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the publication services need, in memory: a fixed clock at 2026-11-02T10:00:00Z (a Monday), one scripted
 * platform, the post, channel, series and external post stores, and an editor and a publisher. Each test builds its own.
 */
public final class PublishFixture {

    public static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    public static final UUID EDITOR = UUID.fromString("e0170000-0000-4000-8000-000000000001");
    public static final UUID PUBLISHER = UUID.fromString("b0b00000-0000-4000-8000-000000000001");
    public static final String TOKEN = "access-token-for-tests";
    public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    public final MutableClock clock = new MutableClock(NOW);
    public final IdentifierGenerator identifiers = new IdentifierGenerator(new DeterministicRandom(7L));
    public final InMemoryChannelStore channels = new InMemoryChannelStore();
    public final InMemoryPostStore posts = new InMemoryPostStore();
    public final InMemoryExternalPostStore externalPosts = new InMemoryExternalPostStore();
    public final InMemorySeriesStore seriesStore = new InMemorySeriesStore();
    public final FixedBrandTokens brand = new FixedBrandTokens().colour("on-primary", "#ffffff")
            .colour("primary", "#6638e4").colour("edge", "#767676").colour("near", "#777777").colour("pale", "#959595")
            .template("short-9x16");
    public final SeriesService series = new SeriesService(clock, identifiers, seriesStore, externalPosts, channels, brand,
            JoinDomain.NONE);
    public final ScriptedAdapter youtube;
    public final PlatformRegistry platforms;
    public final RecordingAudit audit = new RecordingAudit();
    public final ConnectionService connections;
    public final PostService service;
    public final PublicationRunner runner;
    public final PublishSettings settings;

    /** The media of the test storage, keyed by storage key. */
    public final InMemoryMedia media = new InMemoryMedia();
    /** The step-wise platform, when the world was built with step-wise publication. */
    public final ScriptedStepwise stepwise = new ScriptedStepwise();
    public final InMemoryQuotaUsage quotaUsage = new InMemoryQuotaUsage();
    /** The chapter marks of the timelines the finished assets came from; empty unless a test registers some. */
    public final InMemoryTimelineChapters chapters = new InMemoryTimelineChapters();

    /** A world whose platform publishes at its time ({@code native = false}) or accepts a timed publication itself. */
    public PublishFixture(boolean nativeScheduling) {
        this(nativeScheduling, PublishSettings.from(Map.of()));
    }

    public PublishFixture(boolean nativeScheduling, PublishSettings settings) {
        this(nativeScheduling, settings, false);
    }

    /** A world whose platform publishes in steps (the YouTube way) when {@code stepwise} is true. */
    public PublishFixture(boolean nativeScheduling, PublishSettings settings, boolean stepwise) {
        this.youtube = new ScriptedAdapter(nativeScheduling);
        if (stepwise) {
            this.youtube.stepwise(this.stepwise);
        }
        this.platforms = new PlatformRegistry(java.util.List.of(youtube));
        this.settings = settings;
        this.connections = new ConnectionService(new DirectTransactions(), clock, identifiers,
                new SecretGenerator(new DeterministicRandom(9L)), channels, audit, platforms, Duration.ofMinutes(10), 0.8);
        this.service = new PostService(new DirectTransactions(), clock, identifiers, posts, channels, series, platforms,
                connections, BERLIN);
        PublicationRunner.Stepwise steps = stepwise
                ? new PublicationRunner.Stepwise(TokenCipher.of(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)), null, new SecureRandom()),
                media, new QuotaLedger(QuotaBudget.youtubeDefaults(), quotaUsage), chapters)
                : null;
        this.runner = new PublicationRunner(new DirectTransactions(), clock, identifiers, posts, channels, connections,
                platforms, externalPosts, series, settings, steps);
    }

    public static PostActor editor() {
        return new PostActor(EDITOR, Set.of(Role.EDITOR), false);
    }

    public static PostActor publisher() {
        return new PostActor(PUBLISHER, Set.of(Role.PUBLISHER), true);
    }

    /** A publisher acting from a token, not from the web application. */
    public static PostActor publisherFromToken() {
        return new PostActor(PUBLISHER, Set.of(Role.PUBLISHER), false);
    }

    /** A connected channel with a valid grant that lasts for a year, so no test needs a token refresh. */
    public UUID connectChannel(String account) {
        UUID id = UUID.nameUUIDFromBytes(("channel-" + account).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        channels.insertChannel(null, new Channel(id, "youtube", account, "Channel " + account, ConnectionState.CONNECTED,
                null, Set.of(), Set.of(), null, NOW, NOW, null));
        channels.saveGrant(null, new ChannelGrant(id, TOKEN, "refresh-token", NOW.plus(Duration.ofDays(365)), NOW));
        return id;
    }

    /** Puts a channel into the state where its grant was rejected. */
    public void expireChannel(UUID channelId) {
        Channel channel = channels.storedChannel(channelId).orElseThrow();
        channels.updateChannel(null, new Channel(channel.id(), channel.platform(), channel.platformAccountId(),
                channel.displayName(), ConnectionState.NEEDS_RECONNECTION, "The grant was rejected.", channel.features(),
                channel.grantedScopes(), null, channel.createdAt(), NOW, null));
    }

    /** A finished video of the given size in the test storage, for the step-wise publication. */
    public UUID storedVideo(long sizeBytes) {
        UUID id = identifiers.next();
        String key = "assets/" + id;
        posts.registerStoredAsset(id, new FinishedAsset("video", "mp4", key, sizeBytes));
        media.put(key, sizeBytes);
        return id;
    }

    /** A finished video of the given size with its recorded width, height and duration, for the Short format rule. */
    public UUID storedVideo(long sizeBytes, int width, int height, int durationSeconds) {
        UUID id = identifiers.next();
        String key = "assets/" + id;
        posts.registerStoredAsset(id, new FinishedAsset("video", "mp4", key, sizeBytes, width, height, durationSeconds));
        media.put(key, sizeBytes);
        return id;
    }

    /** A finished JPEG cover in the test storage. */
    public UUID storedCover() {
        UUID id = identifiers.next();
        String key = "covers/" + id;
        posts.registerStoredAsset(id, new FinishedAsset("image", "jpg", key, 200_000L));
        media.put(key, 200_000L);
        return id;
    }

    /** A finished video registered in the store; the post service plans only such assets. */
    public UUID finishedVideo() {
        UUID id = identifiers.next();
        posts.registerAsset(id, "video");
        return id;
    }

    /**
     * A series that airs on Mondays at 18 o'clock (Berlin) on the given channel with a playlist. The playlist is one the
     * channel owns on the platform, so a schedule of its posts passes the ownership check.
     */
    public UUID series(String name, UUID channelId) throws java.sql.SQLException {
        youtube.ownsPlaylists("PL-" + name);
        return seriesWithPlaylist(name, channelId, "PL-" + name);
    }

    /** A series that airs on Mondays at 18 o'clock (Berlin) on the given channel, with the given playlist identifier. */
    public UUID seriesWithPlaylist(String name, UUID channelId, String playlistId) throws java.sql.SQLException {
        SeriesDraft draft = new SeriesDraft(name, Map.of("de", "Kurz erklärt"), "primary", "on-primary", "Kurz erklärt:",
                "short-9x16", "#" + name.toLowerCase().replace(" ", ""), Audience.DEVELOPERS.value(), 1, 18, true,
                Map.of(channelId, new net.onelitefeather.menura.core.series.SeriesPlaylist(playlistId, "Playlist " + name)));
        return series.create(null, draft).id();
    }

    public static Content titleOnly(String title) {
        return new Content(null, null, title, null, null, null, null);
    }

    public static Content timeOnly(Instant at) {
        return new Content(null, null, null, null, null, null, at);
    }
}
