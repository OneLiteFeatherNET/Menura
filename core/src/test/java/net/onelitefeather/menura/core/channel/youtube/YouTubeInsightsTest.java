package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.analytics.Metrics;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.channel.youtube.RecordedYouTube.Sent;
import net.onelitefeather.menura.core.post.ExternalPostFormat;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeInsightsTest {

    private static final String READONLY = ChannelFixture.YOUTUBE_READONLY;
    private static final String ANALYTICS = "https://www.googleapis.com/auth/yt-analytics.readonly";
    private static final String UPLOADS = "/youtube/v3/channels";
    private static final String PLAYLIST_ITEMS = "/youtube/v3/playlistItems";
    private static final String VIDEOS = "/youtube/v3/videos";
    private static final String REPORTS = "/v2/reports";

    private static YouTubeAdapter adapter(RecordedYouTube http) {
        return new YouTubeAdapter(Clock.fixed(ChannelFixture.NOW, ZoneOffset.UTC),
                new YouTubeAdapter.Settings("client-id-test", "client-secret-test", ChannelFixture.REDIRECT), http);
    }

    /** A channel whose uploads playlist is read, with the two pages of uploads scripted. */
    private static RecordedYouTube uploads() {
        return new RecordedYouTube()
                .on("GET", UPLOADS, new HttpTransport.Reply(200, RecordedYouTube.json("channel-uploads.json")))
                .when("GET", PLAYLIST_ITEMS, query -> !query.containsKey("pageToken"),
                        new HttpTransport.Reply(200, RecordedYouTube.json("uploads-page-1.json")))
                .when("GET", PLAYLIST_ITEMS, query -> "PAGE2".equals(query.get("pageToken")),
                        new HttpTransport.Reply(200, RecordedYouTube.json("uploads-page-2.json")))
                .when("GET", VIDEOS, query -> query.get("part").equals("contentDetails"),
                        new HttpTransport.Reply(200, RecordedYouTube.json("video-durations.json")));
    }

    private static Map<String, PlatformAdapter.PublishedItem> byId(List<PlatformAdapter.PublishedItem> items) {
        return items.stream().collect(Collectors.toMap(PlatformAdapter.PublishedItem::platformItemId, item -> item));
    }

    // ------------------------------------------------------------ listing

    @Test
    void privateVideosAreExcludedFromTheListing() {
        List<PlatformAdapter.PublishedItem> items = adapter(uploads()).listPublished("ya29.a", 10);

        assertFalse(byId(items).containsKey("vid-private"), "a private video is not part of the archive");
    }

    @Test
    void publicAndUnlistedVideosOfAllPagesAreListed() {
        List<PlatformAdapter.PublishedItem> items = adapter(uploads()).listPublished("ya29.a", 10);

        assertEquals(Set.of("vid-long", "vid-short", "vid-old"), byId(items).keySet(),
                "public and unlisted videos of every page are imported");
    }

    @Test
    void listedItemCarriesIdentifierUrlTitleAndPublishTime() {
        PlatformAdapter.PublishedItem long1 = byId(adapter(uploads()).listPublished("ya29.a", 10)).get("vid-long");

        assertEquals("https://www.youtube.com/watch?v=vid-long", long1.url(), "the URL points at the watch page of the video");
        assertEquals("Long video", long1.title());
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), long1.publishedAt(),
                "the publish time is the time the video was published, not the time it joined the playlist");
    }

    @Test
    void listedItemCarriesTheDescriptionForSeriesAssignment() {
        PlatformAdapter.PublishedItem long1 = byId(adapter(uploads()).listPublished("ya29.a", 10)).get("vid-long");

        assertEquals("Part of #series-one", long1.description(), "the description is the text series assignment reads");
    }

    @Test
    void shortIsListedAsShortAndLongerVideoAsVideo() {
        Map<String, PlatformAdapter.PublishedItem> items = byId(adapter(uploads()).listPublished("ya29.a", 10));

        assertEquals(ExternalPostFormat.SHORT, items.get("vid-short").format(), "a 45 second video is a Short");
        assertEquals(ExternalPostFormat.VIDEO, items.get("vid-long").format(), "a 15 minute video is a video");
    }

    @Test
    void listingStopsAtTheRequestedLimitAndDoesNotReadTheNextPage() {
        RecordedYouTube http = uploads();

        List<PlatformAdapter.PublishedItem> items = adapter(http).listPublished("ya29.a", 2);

        assertEquals(2, items.size(), "at most the requested number of items is returned");
        assertTrue(http.sent().stream().noneMatch(sent -> "PAGE2".equals(RecordedYouTube.query(sent.uri()).get("pageToken"))),
                "the second page is not read once the limit is reached");
    }

    @Test
    void listingMakesOnlyReadRequestsToGoogleHosts() {
        RecordedYouTube http = uploads();

        adapter(http).listPublished("ya29.a", 10);

        assertEquals(Set.of("GET"), http.sent().stream().map(Sent::method).collect(Collectors.toSet()),
                "listing sends no request that writes to YouTube");
        assertEquals(Set.of("www.googleapis.com"), http.sent().stream().map(sent -> sent.uri().getHost())
                .collect(Collectors.toSet()), "listing reaches only the Data API host");
    }

    @Test
    void anAccountWithoutAChannelListsNothing() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", UPLOADS, new HttpTransport.Reply(200, "{\"items\":[]}"));

        assertTrue(adapter(http).listPublished("ya29.a", 10).isEmpty(), "an account without a channel has no uploads");
    }

    @Test
    void anExpiredTokenIsRejectedSoTheConnectionNeedsRefresh() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", UPLOADS, new HttpTransport.Reply(401, "{\"error\":{\"message\":\"Invalid Credentials\"}}"));

        PlatformException failure = assertThrows(PlatformException.class, () -> adapter(http).listPublished("ya29.SECRET", 10));

        assertEquals(PlatformException.Kind.REJECTED, failure.kind(), "an invalid access token is refused, not retried blindly");
        assertFalse(failure.getMessage().contains("ya29.SECRET"), "the token never reaches the error message");
    }

    @Test
    void rateLimitedListingKeepsTheRetryHintOfThePlatform() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", UPLOADS, new HttpTransport.Reply(200, RecordedYouTube.json("channel-uploads.json")))
                .on("GET", PLAYLIST_ITEMS, new HttpTransport.Reply(429, "{}", java.util.Optional.of("60")));

        PlatformException failure = assertThrows(PlatformException.class, () -> adapter(http).listPublished("ya29.a", 10));

        assertEquals(PlatformException.Kind.RATE_LIMITED, failure.kind());
        assertEquals(Duration.ofSeconds(60), failure.retryAfter());
    }

    @Test
    void exhaustedDailyQuotaIsRateLimited() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", UPLOADS, new HttpTransport.Reply(403,
                        "{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}"));

        PlatformException failure = assertThrows(PlatformException.class, () -> adapter(http).listPublished("ya29.a", 10));

        assertEquals(PlatformException.Kind.RATE_LIMITED, failure.kind(), "a quota error waits for the daily reset");
    }

    @Test
    void missingScopeIsRejectedSoTheChannelNeedsConnectingAgain() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", UPLOADS, new HttpTransport.Reply(403,
                        "{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}"));

        PlatformException failure = assertThrows(PlatformException.class, () -> adapter(http).listPublished("ya29.a", 10));

        assertEquals(PlatformException.Kind.REJECTED, failure.kind(), "a grant without the read scope needs a new consent");
    }

    // ------------------------------------------------------------ playlists

    @Test
    void playlistMembershipReturnsTheVideoIdentifiersOfAllPages() {
        Set<String> members = adapter(uploads()).playlistItemIds("ya29.a", "PLexample");

        assertEquals(Set.of("vid-long", "vid-short", "vid-old"), members.stream()
                .filter(id -> !id.equals("vid-private")).collect(Collectors.toSet()),
                "membership is read from every page of the playlist");
    }

    @Test
    void playlistMembershipOfAnUnknownPlaylistIsUnavailable() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", PLAYLIST_ITEMS, new HttpTransport.Reply(404, "{}"));

        PlatformException failure = assertThrows(PlatformException.class,
                () -> adapter(http).playlistItemIds("ya29.a", "PLmissing"));

        assertEquals(PlatformException.Kind.UNAVAILABLE, failure.kind(), "a missing playlist is not read as an empty one");
    }

    // ------------------------------------------------------------ metrics

    private static RecordedYouTube metrics(String analyticsReport) {
        return new RecordedYouTube()
                .when("GET", VIDEOS, query -> "statistics".equals(query.get("part")),
                        new HttpTransport.Reply(200, RecordedYouTube.json("video-statistics.json")))
                .when("GET", REPORTS, query -> true, new HttpTransport.Reply(200, analyticsReport));
    }

    private static final Set<String> ALL = Metrics.requested();

    @Test
    void viewsLikesAndCommentsComeFromTheDataApiStatistics() {
        Map<String, Map<String, Long>> readings = adapter(metrics(RecordedYouTube.json("analytics-report.json")))
                .readMetrics("ya29.a", List.of("vid-long"), Set.of(Metrics.VIEWS, Metrics.LIKES, Metrics.COMMENTS));

        assertEquals(Map.of("views", 1200L, "likes", 80L, "comments", 7L), readings.get("vid-long"),
                "the Data API statistics give the views, likes and comments of the video");
    }

    @Test
    void watchTimeIsReportedInSecondsFromTheMinutesOfTheAnalyticsApi() {
        Map<String, Map<String, Long>> readings = adapter(metrics(RecordedYouTube.json("analytics-report.json")))
                .readMetrics("ya29.a", List.of("vid-long"), ALL);

        assertEquals(750L, readings.get("vid-long").get(Metrics.WATCH_TIME),
                "12.5 estimated minutes watched are 750 seconds of watch time");
    }

    @Test
    void engagedViewsSharesAndAverageViewPercentageComeFromTheAnalyticsApi() {
        Map<String, Long> values = adapter(metrics(RecordedYouTube.json("analytics-report.json")))
                .readMetrics("ya29.a", List.of("vid-long"), ALL).get("vid-long");

        assertEquals(300L, values.get(Metrics.ENGAGED_VIEWS), "engaged views are the analytics count");
        assertEquals(3L, values.get(Metrics.SHARES), "shares are the analytics count");
        assertEquals(41L, values.get(Metrics.AVERAGE_VIEW_PERCENTAGE), "the average view percentage is rounded to whole percent");
    }

    @Test
    void hiddenLikesAreLeftOutAndNeverWrittenAsZero() {
        Map<String, Long> values = adapter(metrics(RecordedYouTube.json("analytics-report.json")))
                .readMetrics("ya29.a", List.of("vid-short"), ALL).get("vid-short");

        assertFalse(values.containsKey(Metrics.LIKES), "a like count the platform does not return is unavailable, not zero");
        assertEquals(950L, values.get(Metrics.VIEWS));
    }

    @Test
    void aMetricMissingFromTheAnalyticsReportIsLeftOut() {
        String reportWithoutEngagedViews = """
                {"columnHeaders":[{"name":"video","columnType":"DIMENSION","dataType":"STRING"},\
                {"name":"shares","columnType":"METRIC","dataType":"INTEGER"}],"rows":[["vid-long",3]]}""";

        Map<String, Long> values = adapter(metrics(reportWithoutEngagedViews))
                .readMetrics("ya29.a", List.of("vid-long"), ALL).get("vid-long");

        assertFalse(values.containsKey(Metrics.ENGAGED_VIEWS), "a metric the report does not carry is unavailable, not zero");
    }

    @Test
    void anItemThePlatformDoesNotReturnIsLeftOutOfTheResult() {
        Map<String, Map<String, Long>> readings = adapter(metrics(RecordedYouTube.json("analytics-report.json")))
                .readMetrics("ya29.a", List.of("vid-long", "vid-deleted"), ALL);

        assertFalse(readings.containsKey("vid-deleted"), "an item the platform does not return is not written as zero");
    }

    @Test
    void batchOf120ItemsSplitsIntoCallsOfFiftyFiftyAndTwenty() {
        RecordedYouTube http = metrics(RecordedYouTube.json("analytics-report.json"));
        List<String> ids = IntStream.range(0, 120).mapToObj(i -> "vid-" + i).toList();

        adapter(http).readMetrics("ya29.a", ids.subList(0, 50), ALL);
        adapter(http).readMetrics("ya29.a", ids.subList(50, 100), ALL);
        adapter(http).readMetrics("ya29.a", ids.subList(100, 120), ALL);

        List<Integer> sizes = http.sent().stream()
                .filter(sent -> VIDEOS.equals(sent.uri().getPath()))
                .map(sent -> query(sent).get("id").split(",").length)
                .toList();
        assertEquals(List.of(50, 50, 20), sizes, "the videos.list call takes at most fifty identifiers per call");
        assertEquals(50, adapter(http).metricsBatchSize(), "the adapter declares the platform batch limit of fifty");
    }

    @Test
    void analyticsRequestNamesOnlyTheVideosOfTheBatch() {
        RecordedYouTube http = metrics(RecordedYouTube.json("analytics-report.json"));

        adapter(http).readMetrics("ya29.a", List.of("vid-long", "vid-short"), ALL);

        Map<String, String> report = query(http.sent().stream().filter(sent -> REPORTS.equals(sent.uri().getPath()))
                .findFirst().orElseThrow().uri());
        assertEquals("channel==MINE", report.get("ids"), "the report covers the channel of the token");
        assertEquals("video", report.get("dimensions"), "the report is broken down per video");
        assertEquals("video==vid-long,vid-short", report.get("filters"), "only the batch videos are requested");
    }

    @Test
    void aFailedBatchFailsTheWholeCall() {
        RecordedYouTube http = new RecordedYouTube()
                .when("GET", VIDEOS, query -> "statistics".equals(query.get("part")),
                        new HttpTransport.Reply(429, "{}"));

        PlatformException failure = assertThrows(PlatformException.class,
                () -> adapter(http).readMetrics("ya29.a", List.of("vid-long"), ALL));

        assertEquals(PlatformException.Kind.RATE_LIMITED, failure.kind(), "the call fails as a whole, none of its items are read");
    }

    // ------------------------------------------------------------ permissions and capabilities

    @Test
    void statisticsFeatureRequestsOnlyReadScopes() {
        YouTubeAdapter youtube = adapter(new RecordedYouTube());

        assertEquals(Set.of(READONLY, ANALYTICS), youtube.scopesFor(Set.of("insights")),
                "the statistics feature needs the Data API read scope and the Analytics read scope, nothing else");
    }

    @Test
    void consentForStatisticsOnlyCarriesNoWriteLiveOrCommentScope() {
        YouTubeAdapter youtube = adapter(new RecordedYouTube());

        String scope = query(youtube.authorizationUri(new PlatformAdapter.ConsentRequest("s", "c", ChannelFixture.REDIRECT,
                youtube.scopesFor(Set.of("insights"))))).get("scope");

        assertEquals(Set.of(READONLY, ANALYTICS), Set.of(scope.split(" ")), "the consent asks for read access only");
        assertFalse(scope.contains("upload") || scope.contains("force-ssl") || scope.contains("auth/youtube "),
                "no upload, comment or live stream scope is requested");
    }

    @Test
    void listingAndMetricsAreDeclaredSupportedWhilePublishingIsNot() {
        PlatformCapabilities capabilities = adapter(new RecordedYouTube()).capabilities();

        assertTrue(capabilities.listing(), "the uploads of a channel can be listed");
        assertTrue(capabilities.metrics(), "the statistics of a video can be read");
        assertFalse(capabilities.publishing(), "publishing lands in the YouTube integration change");
    }

    private static Map<String, String> query(Sent sent) {
        return query(sent.uri());
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).collect(Collectors.toMap(
                pair -> URLDecoder.decode(pair.substring(0, pair.indexOf('=')), StandardCharsets.UTF_8),
                pair -> URLDecoder.decode(pair.substring(pair.indexOf('=') + 1), StandardCharsets.UTF_8)));
    }
}
