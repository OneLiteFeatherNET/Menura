package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.StepwisePublisher;
import net.onelitefeather.menura.core.channel.StepwisePublisher.UploadProgress;
import net.onelitefeather.menura.core.channel.StepwisePublisher.UploadStart;
import net.onelitefeather.menura.core.channel.StepwisePublisher.VideoState;
import net.onelitefeather.menura.core.channel.UploadSessionExpired;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The step-wise publication of the YouTube adapter against a scripted transport: no test reaches the platform, no token is
 * real, and the clock is fixed. The request shapes follow the resumable upload guide and the videos, playlistItems and
 * thumbnails references checked 2026-11-08 (see the comments in YouTubeAdapter).
 */
class YouTubeUploadTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final String UPLOAD = "/upload/youtube/v3/videos";
    private static final String SESSION = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&upload_id=session-1";
    private static final String TOKEN = "token-for-tests";
    private static final long SIZE = 1_000_000L;

    @Test
    void theUploadStartSendsTheMetadataAndBothDeclarationsExplicitly() {
        ScriptedHttp http = new ScriptedHttp().call("POST", UPLOAD, new HttpTransport.Answer(200, "",
                Optional.of(SESSION), Optional.empty(), Optional.empty()));
        YouTubeAdapter youtube = adapter(http);

        String session = youtube.startUpload(TOKEN, start(Visibility.PUBLIC, false, null));

        assertEquals(SESSION, session, "the session location of the platform is returned");
        ScriptedHttp.Sent sent = http.sent().get(0);
        assertTrue(sent.uri().getRawQuery().contains("uploadType=resumable"), "the upload is resumable");
        assertTrue(sent.body().contains("\"privacyStatus\":\"public\""), "the privacy status is sent explicitly");
        assertTrue(sent.body().contains("\"selfDeclaredMadeForKids\":false"), "the made-for-kids value is sent explicitly");
        assertTrue(sent.body().contains("\"title\":\"A title\""), "the title of the post is sent");
        assertEquals(String.valueOf(SIZE), sent.headers().get("X-Upload-Content-Length"), "the size is announced");
        assertEquals("video/mp4", sent.headers().get("X-Upload-Content-Type"), "the content type is announced");
    }

    @Test
    void aTimedUploadIsSentAsPrivateWithThePublishTime() {
        ScriptedHttp http = new ScriptedHttp().call("POST", UPLOAD, new HttpTransport.Answer(200, "",
                Optional.of(SESSION), Optional.empty(), Optional.empty()));
        Instant publishAt = Instant.parse("2026-11-09T17:00:00Z");

        adapter(http).startUpload(TOKEN, start(Visibility.PRIVATE, true, publishAt));

        String body = http.sent().get(0).body();
        assertTrue(body.contains("\"privacyStatus\":\"private\""), "a timed upload is private");
        assertTrue(body.contains("\"publishAt\":\"2026-11-09T17:00:00Z\""), "the publish time is the scheduled time");
    }

    @Test
    void anUploadWithoutAnExplicitMadeForKidsDeclarationIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadStart("A title", "", List.of(), Visibility.PUBLIC, null, null, SIZE, "video/mp4"),
                "the made-for-kids declaration must be given, never left to a default");
    }

    @Test
    void anUploadWithoutAnExplicitVisibilityIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadStart("A title", "", List.of(), null, false, null, SIZE, "video/mp4"),
                "the visibility must be given, never left to a default");
    }

    @Test
    void aTimedPublicUploadIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> start(Visibility.PUBLIC, false, Instant.parse("2026-11-09T17:00:00Z")),
                "a publish time requires the private status, because the platform honours it only for private videos");
    }

    @Test
    void aChunkIsSentWithItsRangeAndTheConfirmedBytesAreReadFromTheAnswer() {
        ScriptedHttp http = new ScriptedHttp().call("PUT", "/upload/youtube/v3/videos", new HttpTransport.Answer(308,
                "", Optional.empty(), Optional.of("bytes=0-262143"), Optional.empty()));

        UploadProgress progress = adapter(http).sendChunk(TOKEN, SESSION, 0, SIZE, "video/mp4", new byte[262_144]);

        assertEquals(262_144L, progress.confirmedBytes(), "the platform confirmed the first chunk");
        assertFalse(progress.complete(), "the upload is not complete after the first chunk");
        assertEquals("bytes 0-262143/1000000", http.sent().get(0).headers().get("Content-Range"),
                "the chunk names its byte range and the total");
    }

    @Test
    void anInterruptedUploadResumesFromTheLastConfirmedByteInsteadOfStartingOver() {
        ScriptedHttp http = new ScriptedHttp()
                .call("PUT", "/upload/youtube/v3/videos", new HttpTransport.Answer(308, "",
                        Optional.empty(), Optional.of("bytes=0-524287"), Optional.empty()))
                .call("PUT", "/upload/youtube/v3/videos", new HttpTransport.Answer(200, "{\"id\":\"vid123\"}",
                        Optional.empty(), Optional.empty(), Optional.empty()));
        YouTubeAdapter youtube = adapter(http);

        UploadProgress status = youtube.uploadStatus(TOKEN, SESSION, SIZE);
        UploadProgress resumed = youtube.sendChunk(TOKEN, SESSION, status.confirmedBytes(), SIZE, "video/mp4",
                new byte[(int) (SIZE - status.confirmedBytes())]);

        assertEquals(524_288L, status.confirmedBytes(), "the status query reports the bytes the platform holds");
        assertEquals("bytes */1000000", http.sent().get(0).headers().get("Content-Range"),
                "the status query names the total without sending bytes");
        assertEquals("bytes 524288-999999/1000000", http.sent().get(1).headers().get("Content-Range"),
                "the resumed chunk starts at the first byte the platform does not hold");
        assertTrue(resumed.complete(), "the last chunk completes the upload");
        assertEquals("vid123", resumed.platformItemId(), "the platform item of the completed upload is returned");
    }

    @Test
    void aStatusQueryWithoutARangeMeansNothingWasReceived() {
        ScriptedHttp http = new ScriptedHttp().call("PUT", "/upload/youtube/v3/videos", new HttpTransport.Answer(308,
                "", Optional.empty(), Optional.empty(), Optional.empty()));

        assertEquals(0L, adapter(http).uploadStatus(TOKEN, SESSION, SIZE).confirmedBytes(),
                "a missing Range header means the platform holds no bytes yet");
    }

    @Test
    void anExpiredSessionIsReportedSoThatTheCallerStartsANewUpload() {
        ScriptedHttp http = new ScriptedHttp().call("PUT", "/upload/youtube/v3/videos", new HttpTransport.Answer(404,
                "{}", Optional.empty(), Optional.empty(), Optional.empty()));

        assertThrows(UploadSessionExpired.class, () -> adapter(http).uploadStatus(TOKEN, SESSION, SIZE),
                "an expired session is a distinct failure, not a generic rejection");
    }

    @Test
    void aPlaylistInsertNamesThePlaylistAndTheVideo() {
        ScriptedHttp http = new ScriptedHttp().call("POST", "/youtube/v3/playlistItems", new HttpTransport.Answer(200,
                "{}", Optional.empty(), Optional.empty(), Optional.empty()));

        adapter(http).addToPlaylist(TOKEN, "PL-series", "vid123");

        ScriptedHttp.Sent sent = http.sent().get(0);
        assertTrue(sent.body().contains("\"playlistId\":\"PL-series\""), "the playlist is named");
        assertTrue(sent.body().contains("\"videoId\":\"vid123\""), "the video is named as a resource");
    }

    @Test
    void aVideoAlreadyInThePlaylistIsNotAFailureWhenARetryInsertsItAgain() {
        ScriptedHttp http = new ScriptedHttp().call("POST", "/youtube/v3/playlistItems", new HttpTransport.Answer(409,
                "{}", Optional.empty(), Optional.empty(), Optional.empty()));

        adapter(http).addToPlaylist(TOKEN, "PL-series", "vid123");

        assertEquals(1, http.sent().size(), "the insert was attempted once and the conflict is the desired end state");
    }

    @Test
    void theReadBackReportsThePrivacyThePublishTimeAndTheProcessingState() {
        ScriptedHttp http = new ScriptedHttp().when("GET", "/youtube/v3/videos", query -> true,
                new HttpTransport.Reply(200, RecordedYouTube.json("video-status.json")));

        VideoState state = adapter(http).video(TOKEN, "vid123");

        assertEquals(Visibility.PRIVATE, state.visibility(), "the applied privacy status is read back");
        assertEquals(Boolean.FALSE, state.madeForKids(), "the applied made-for-kids value is read back");
        assertEquals(Instant.parse("2026-11-09T17:00:00Z"), state.publishAt(), "the applied publish time is read back");
        assertTrue(state.processed(), "a succeeded processing status means the video is processed");
    }

    @Test
    void aVideoThatIsStillProcessingIsNotReportedAsProcessed() {
        ScriptedHttp http = new ScriptedHttp().when("GET", "/youtube/v3/videos", query -> true,
                new HttpTransport.Reply(200, "{\"items\":[{\"id\":\"vid123\",\"status\":{\"privacyStatus\":\"public\"},"
                        + "\"processingDetails\":{\"processingStatus\":\"processing\"}}]}"));

        VideoState state = adapter(http).video(TOKEN, "vid123");

        assertFalse(state.processed(), "the thumbnail must wait until the video has finished processing");
        assertEquals(Visibility.PUBLIC, state.visibility(), "the privacy is read even while processing");
    }

    @Test
    void aThumbnailIsUploadedAsMediaOfTheVideo() {
        ScriptedHttp http = new ScriptedHttp().call("POST", "/upload/youtube/v3/thumbnails/set", new HttpTransport.Answer(
                200, "{}", Optional.empty(), Optional.empty(), Optional.empty()));

        adapter(http).setThumbnail(TOKEN, "vid123", "image/png", new byte[]{1, 2, 3});

        ScriptedHttp.Sent sent = http.sent().get(0);
        assertTrue(sent.uri().getRawQuery().contains("videoId=vid123"), "the thumbnail names its video");
        assertTrue(sent.uri().getRawQuery().contains("uploadType=media"), "the thumbnail is sent as media");
        assertEquals("image/png", sent.headers().get("Content-Type"), "the image type is sent");
    }

    @Test
    void aRateLimitedChunkIsClassifiedAsRateLimitedSoItIsRetried() {
        ScriptedHttp http = new ScriptedHttp().call("PUT", "/upload/youtube/v3/videos", new HttpTransport.Answer(429,
                "{}", Optional.empty(), Optional.empty(), Optional.of("60")));

        PlatformException failure = assertThrows(PlatformException.class,
                () -> adapter(http).sendChunk(TOKEN, SESSION, 0, SIZE, "video/mp4", new byte[262_144]));

        assertEquals(PlatformException.Kind.RATE_LIMITED, failure.kind(), "a 429 is a rate limit");
        assertEquals(60, failure.retryAfter().toSeconds(), "the platform's retry hint is kept");
    }

    // ------------------------------------------------------------ fixtures

    private static YouTubeAdapter adapter(HttpTransport http) {
        return new YouTubeAdapter(Clock.fixed(NOW, ZoneOffset.UTC),
                new YouTubeAdapter.Settings("client", "secret", "https://menura.example/callback"), http);
    }

    private static UploadStart start(Visibility visibility, boolean madeForKids, Instant publishAt) {
        return new UploadStart("A title", "The description", List.of("#tag"), visibility, madeForKids, publishAt, SIZE,
                "video/mp4");
    }

    /** A transport that answers scripted routes and records each request, with its headers and body as text. */
    static final class ScriptedHttp implements HttpTransport {

        record Sent(String method, URI uri, Map<String, String> headers, String body) {
        }

        private record Route(String method, String path, HttpTransport.Answer answer) {
        }

        private final List<Route> routes = new ArrayList<>();
        private final List<Sent> sent = new ArrayList<>();
        private final Map<String, Reply> replies = new java.util.HashMap<>();

        ScriptedHttp call(String method, String path, HttpTransport.Answer answer) {
            routes.add(new Route(method, path, answer));
            return this;
        }

        ScriptedHttp when(String method, String path, java.util.function.Predicate<Map<String, String>> query,
                          Reply reply) {
            replies.put(method + " " + path, reply);
            return this;
        }

        @Override
        public Reply send(String method, URI uri, String bearerToken, String formBody) {
            sent.add(new Sent(method, uri, Map.of(), formBody == null ? "" : formBody));
            Reply reply = replies.get(method + " " + uri.getPath());
            if (reply == null) {
                throw new AssertionError("unscripted request " + method + " " + uri.getPath());
            }
            return reply;
        }

        @Override
        public Answer call(String method, URI uri, String bearerToken, Map<String, String> headers, byte[] body) {
            sent.add(new Sent(method, uri, Map.copyOf(headers), body == null ? "" : new String(body, StandardCharsets.UTF_8)));
            for (Route route : routes) {
                if (route.method().equals(method) && route.path().equals(uri.getPath())) {
                    routes.remove(route);
                    return route.answer();
                }
            }
            throw new AssertionError("unscripted request " + method + " " + uri.getPath());
        }

        List<Sent> sent() {
            return List.copyOf(sent);
        }
    }
}
