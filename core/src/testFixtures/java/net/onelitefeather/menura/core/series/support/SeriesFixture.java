package net.onelitefeather.menura.core.series.support;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.support.DeterministicRandom;
import net.onelitefeather.menura.core.channel.support.InMemoryChannelStore;
import net.onelitefeather.menura.core.channel.support.MutableClock;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.support.InMemoryExternalPostStore;
import net.onelitefeather.menura.core.series.Audience;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.series.SeriesDraft;
import net.onelitefeather.menura.core.series.SeriesPlaylist;
import net.onelitefeather.menura.core.series.SeriesService;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One fresh world per test: an injected clock, in-memory series, posts and channels, a brand kit with a fixed set of
 * tokens, and the service over them. The tokens are chosen so that their contrast ratios are known:
 * {@code edge} (#767676) on {@code on-primary} (#ffffff) is 4.54:1, {@code near} (#777777) is 4.48:1 and {@code pale}
 * (#959595) is 2.99:1; {@code primary} (#6638e4) is 6.46:1.
 */
public final class SeriesFixture {

    public static final Instant NOW = ChannelFixture.NOW;
    public static final String TEMPLATE = "short-9x16";

    public final MutableClock clock = new MutableClock(NOW);
    public final InMemorySeriesStore series = new InMemorySeriesStore();
    public final InMemoryExternalPostStore posts = new InMemoryExternalPostStore();
    public final InMemoryChannelStore channels = new InMemoryChannelStore();
    public final FixedBrandTokens brand = new FixedBrandTokens()
            .colour("on-primary", "#ffffff")
            .colour("primary", "#6638e4")
            .colour("edge", "#767676")
            .colour("near", "#777777")
            .colour("pale", "#959595")
            .template(TEMPLATE);
    public final SeriesService service;

    public SeriesFixture() {
        this(JoinDomain.NONE);
    }

    public SeriesFixture(JoinDomain joinDomain) {
        this.service = new SeriesService(clock, new IdentifierGenerator(new DeterministicRandom(11L)), series, posts,
                channels, brand, joinDomain);
    }

    /** A valid draft: developers, Monday at 18 o'clock, the primary colour with white badge text, no playlist. */
    public static SeriesDraft draft(String name, String hashtag) {
        return new SeriesDraft(name, Map.of("de", "Kurz erklärt"), "primary", "on-primary", "Kurz erklärt:", TEMPLATE,
                hashtag, Audience.DEVELOPERS.value(), 1, 18, true, Map.of());
    }

    /** A valid draft with a playlist on one channel. */
    public static SeriesDraft draftWithPlaylist(String name, String hashtag, UUID channel, String playlistId) {
        SeriesDraft base = draft(name, hashtag);
        return new SeriesDraft(base.name(), base.badgeText(), base.colourToken(), base.badgeToken(),
                base.openingFormula(), base.layoutTemplate(), base.hashtag(), base.audience(), base.weekday(),
                base.postingSlot(), base.active(), Map.of(channel, new SeriesPlaylist(playlistId, "Playlist " + playlistId)));
    }

    /** A connected channel of the listing platform, stored in this world. */
    public UUID channel(String account) {
        UUID id = UUID.nameUUIDFromBytes(("series-" + account).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        channels.insertChannel(null, new Channel(id, "listing", account, "Channel " + account,
                ConnectionState.CONNECTED, null, Set.of(), Set.of(), null, NOW, NOW, null));
        return id;
    }

    /** A post of a channel, as the import stored it. */
    public static ExternalPost post(UUID channel, String itemId, String format, Instant publishedAt) {
        return new ExternalPost(UUID.nameUUIDFromBytes(("post-" + itemId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                channel, "listing", itemId, "https://video.example.test/" + itemId, "Title " + itemId, format,
                publishedAt, null, NOW, NOW);
    }
}
