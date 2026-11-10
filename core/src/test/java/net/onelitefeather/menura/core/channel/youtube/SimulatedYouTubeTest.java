package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformAdapter.AccountIdentity;
import net.onelitefeather.menura.core.channel.PlatformAdapter.ConsentRequest;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.channel.StepwisePublisher;
import net.onelitefeather.menura.core.channel.StepwisePublisher.UploadProgress;
import net.onelitefeather.menura.core.channel.StepwisePublisher.UploadStart;
import net.onelitefeather.menura.core.channel.support.MutableClock;
import net.onelitefeather.menura.core.post.ExternalPostFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static net.onelitefeather.menura.core.analytics.Metrics.AVERAGE_VIEW_PERCENTAGE;
import static net.onelitefeather.menura.core.analytics.Metrics.COMMENTS;
import static net.onelitefeather.menura.core.analytics.Metrics.ENGAGED_VIEWS;
import static net.onelitefeather.menura.core.analytics.Metrics.LIKES;
import static net.onelitefeather.menura.core.analytics.Metrics.SHARES;
import static net.onelitefeather.menura.core.analytics.Metrics.VIEWS;
import static net.onelitefeather.menura.core.analytics.Metrics.WATCH_TIME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real {@link YouTubeAdapter} running against {@link SimulatedYouTube}: the request and response handling is the
 * adapter's own, the answers are generated. No test reaches the network, the clock is fixed and moved explicitly, and
 * every test builds its own simulator.
 */
class SimulatedYouTubeTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final String REDIRECT = "https://menura.example/callback";
    private static final YouTubeAdapter.Settings SETTINGS = new YouTubeAdapter.Settings("client", "secret", REDIRECT);
    private static final long SIZE = 1_000L;
    private static final long SEED = 7L;

    @Test
    void identifyReturnsTheSimulatedChannel() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);

        Optional<AccountIdentity> identity = youtube.identify(token(youtube));

        assertEquals(Optional.of(new AccountIdentity(SimulatedYouTube.CHANNEL_ID, SimulatedYouTube.CHANNEL_TITLE)),
                identity, "the account of the simulated grant is the simulated channel");
    }

    @Test
    void listPublishedReturnsTheBackCatalogueWithoutThePrivateVideos() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);

        List<PublishedItem> items = youtube.listPublished(token(youtube), 50);

        assertEquals(20, items.size(), "the catalogue has 24 videos of which 4 are private, so 20 are listed");
        long shorts = items.stream().filter(item -> ExternalPostFormat.SHORT.equals(item.format())).count();
        assertEquals(8, shorts, "every third video of the catalogue is a short, and none of them is private");
    }

    @Test
    void theBackCatalogueIsTheSameForTheSameSeedAndClock() {
        MutableClock clock = new MutableClock(NOW);
        List<PublishedItem> first = listing(clock, SEED);

        List<PublishedItem> second = listing(clock, SEED);

        assertEquals(first, second, "the generated data depends only on the seed and the clock");
    }

    @Test
    void readMetricsReturnsViewsLikesAndCommentsOfTheListedVideos() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);
        String token = token(youtube);
        List<String> ids = youtube.listPublished(token, 3).stream().map(PublishedItem::platformItemId).toList();

        Map<String, Map<String, Long>> metrics = youtube.readMetrics(token, ids, Set.of(VIEWS, LIKES, COMMENTS));

        for (String id : ids) {
            assertTrue(metrics.containsKey(id) && metrics.get(id).keySet().containsAll(Set.of(VIEWS, LIKES, COMMENTS)),
                    "the statistics of " + id + " carry views, likes and comments");
        }
    }

    @Test
    void readMetricsReturnsTheAnalyticsFiguresOfTheListedVideos() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);
        String token = token(youtube);
        List<String> ids = youtube.listPublished(token, 3).stream().map(PublishedItem::platformItemId).toList();

        Map<String, Map<String, Long>> metrics = youtube.readMetrics(token, ids,
                Set.of(ENGAGED_VIEWS, AVERAGE_VIEW_PERCENTAGE, WATCH_TIME, SHARES));

        for (String id : ids) {
            assertTrue(metrics.containsKey(id) && metrics.get(id).keySet()
                            .containsAll(Set.of(ENGAGED_VIEWS, AVERAGE_VIEW_PERCENTAGE, WATCH_TIME, SHARES)),
                    "the analytics report carries the four figures of " + id);
        }
    }

    @Test
    void statisticsGrowAsTheClockAdvances() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);
        String token = token(youtube);
        String id = youtube.listPublished(token, 1).get(0).platformItemId();
        long before = youtube.readMetrics(token, List.of(id), Set.of(VIEWS)).get(id).get(VIEWS);

        clock.advance(Duration.ofDays(3));
        long after = youtube.readMetrics(token, List.of(id), Set.of(VIEWS)).get(id).get(VIEWS);

        assertTrue(after > before, "the views of a video grow as the clock advances: " + before + " then " + after);
    }

    @Test
    void aScheduledUploadEndsPrivateAndBecomesPublicOnceThePublishTimeHasPassed() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);
        String token = token(youtube);
        Instant publishAt = NOW.plus(Duration.ofDays(1));
        StepwisePublisher steps = youtube.stepwise().orElseThrow();

        String session = steps.startUpload(token, new UploadStart("Scheduled", "A description", List.of("#tag"),
                Visibility.PRIVATE, false, publishAt, SIZE, "video/mp4"));
        UploadProgress progress = steps.sendChunk(token, session, 0, SIZE, "video/mp4", new byte[(int) SIZE]);
        assertTrue(progress.complete(), "the last byte completes the upload");
        String id = progress.platformItemId();

        StepwisePublisher.VideoState before = steps.video(token, id);
        assertEquals(Visibility.PRIVATE, before.visibility(), "a timed upload is private before its publish time");
        assertEquals(publishAt, before.publishAt(), "the publish time is read back");

        clock.advance(Duration.ofDays(2));

        assertEquals(Visibility.PUBLIC, steps.video(token, id).visibility(),
                "the video is public once the clock has passed the publish time");
    }

    @Test
    void aVideoAddedToAPlaylistIsListedAsAMemberOfIt() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);
        String token = token(youtube);
        StepwisePublisher steps = youtube.stepwise().orElseThrow();
        String session = steps.startUpload(token, new UploadStart("Upload", "", List.of(), Visibility.PUBLIC, false,
                null, SIZE, "video/mp4"));
        String id = steps.sendChunk(token, session, 0, SIZE, "video/mp4", new byte[(int) SIZE]).platformItemId();
        String playlist = SimulatedYouTube.PLAYLIST_IDS.get(0);

        steps.addToPlaylist(token, playlist, id);

        assertTrue(youtube.playlistItemIds(token, playlist).contains(id), "the insert is recorded on the playlist");
    }

    @Test
    void aThumbnailCanBeSetOnAnUploadedVideo() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);
        String token = token(youtube);
        StepwisePublisher steps = youtube.stepwise().orElseThrow();
        String session = steps.startUpload(token, new UploadStart("Upload", "", List.of(), Visibility.PUBLIC, false,
                null, SIZE, "video/mp4"));
        String id = steps.sendChunk(token, session, 0, SIZE, "video/mp4", new byte[(int) SIZE]).platformItemId();

        assertDoesNotThrow(() -> steps.setThumbnail(token, id, "image/png", new byte[]{1, 2, 3}),
                "the thumbnail of a processed upload is accepted");
    }

    @Test
    void ownedPlaylistIdsReturnsTheThreePlaylistsOfTheChannel() {
        MutableClock clock = new MutableClock(NOW);
        YouTubeAdapter youtube = simulated(clock);

        Set<String> owned = youtube.ownedPlaylistIds(token(youtube));

        assertEquals(Set.of("PLsimulated-series", "PLsimulated-shorts", "PLsimulated-archive"), owned,
                "the channel owns three playlists");
    }

    @Test
    void inSimulatedModeTheConsentUriGoesStraightToTheRedirectWithAStateAndACode() {
        MutableClock clock = new MutableClock(NOW);
        PlatformAdapter adapter = YouTubeAdapters.create(clock, SETTINGS, YouTubeMode.SIMULATED, noLiveTraffic());

        URI uri = adapter.authorizationUri(new ConsentRequest("state-123", "challenge", REDIRECT,
                Set.of(YouTubeAdapter.READONLY_SCOPE)));

        assertTrue(uri.toString().startsWith(REDIRECT + "?"), "the consent URI is the redirect URI itself: " + uri);
        Map<String, String> query = SimulatedYouTube.decode(uri.getRawQuery());
        assertEquals("state-123", query.get("state"), "the state is handed back unchanged");
        assertEquals(SimulatedYouTube.AUTHORIZATION_CODE, query.get("code"), "the callback receives a code directly");
    }

    @Test
    void inSimulatedModeNoRequestReachesTheLiveTransport() {
        MutableClock clock = new MutableClock(NOW);
        PlatformAdapter adapter = YouTubeAdapters.create(clock, SETTINGS, YouTubeMode.SIMULATED, noLiveTraffic());

        Optional<AccountIdentity> identity = adapter.identify(adapter.exchangeCode(
                SimulatedYouTube.AUTHORIZATION_CODE, "verifier", REDIRECT).accessToken());

        assertTrue(identity.isPresent(), "the simulated channel is found without the live transport");
    }

    @Test
    void aRequestOutsideTheGoogleHostsFailsWithAnIOException() {
        SimulatedYouTube simulator = new SimulatedYouTube(new MutableClock(NOW), SEED);
        URI outside = URI.create("https://example.com/youtube/v3/channels?mine=true");

        IOException failure = assertThrows(IOException.class,
                () -> simulator.send("GET", outside, SimulatedYouTube.ACCESS_TOKEN, null),
                "a URI outside the Google hosts is refused");

        assertTrue(failure.getMessage().contains("example.com"), "the refusal names the URI: " + failure.getMessage());
    }

    // ------------------------------------------------------------ fixtures

    private static List<PublishedItem> listing(MutableClock clock, long seed) {
        YouTubeAdapter youtube = new YouTubeAdapter(clock, SETTINGS, new SimulatedYouTube(clock, seed));
        return youtube.listPublished(token(youtube), 50);
    }

    private static YouTubeAdapter simulated(MutableClock clock) {
        return new YouTubeAdapter(clock, SETTINGS, new SimulatedYouTube(clock, SEED));
    }

    private static String token(YouTubeAdapter youtube) {
        return youtube.exchangeCode(SimulatedYouTube.AUTHORIZATION_CODE, "verifier", REDIRECT).accessToken();
    }

    /** A live transport that fails the test if anything is sent through it. */
    private static HttpTransport noLiveTraffic() {
        return new HttpTransport() {
            @Override
            public Reply send(String method, URI uri, String bearerToken, String formBody) {
                throw new AssertionError("a request reached the live transport: " + method + " " + uri);
            }
        };
    }
}
