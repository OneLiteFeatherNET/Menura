package net.onelitefeather.menura.core.channel.youtube;

import com.nimbusds.jose.util.JSONObjectUtils;
import net.onelitefeather.menura.core.analytics.Metrics;
import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformFeature;
import net.onelitefeather.menura.core.channel.StepwisePublisher;
import net.onelitefeather.menura.core.channel.UploadSessionExpired;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
public final class YouTubeAdapter implements PlatformAdapter, StepwisePublisher {

    public static final String PLATFORM = "youtube";

    /** The videos.list call takes at most 50 video identifiers; one metrics read batches up to that many. */
    static final int YOUTUBE_BATCH_SIZE = 50;
    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    static final String REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    static final String CHANNELS_ENDPOINT = "https://www.googleapis.com/youtube/v3/channels";
    static final String PLAYLIST_ITEMS_ENDPOINT = "https://www.googleapis.com/youtube/v3/playlistItems";
    static final String VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos";
    static final String ANALYTICS_ENDPOINT = "https://youtubeanalytics.googleapis.com/v2/reports";
    static final Set<String> HOSTS = Set.of("accounts.google.com", "oauth2.googleapis.com", "www.googleapis.com",
            "youtubeanalytics.googleapis.com");

    static final String READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
    static final String UPLOAD_SCOPE = "https://www.googleapis.com/auth/youtube.upload";
    /**
     * Needed by playlistItems.insert, which does not accept youtube.upload (checked 2026-11-08 at
     * https://developers.google.com/youtube/v3/docs/playlistItems/insert: youtubepartner, youtube or youtube.force-ssl).
     * youtube.force-ssl is the narrowest of them that thumbnails.set and playlistItems.insert both accept.
     */
    static final String FORCE_SSL_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl";
    /**
     * The resumable upload endpoint (checked 2026-11-08 at
     * https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol and the videos.insert reference).
     */
    static final String UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/youtube/v3/videos";
    static final String THUMBNAIL_ENDPOINT = "https://www.googleapis.com/upload/youtube/v3/thumbnails/set";
    /** Chunks of a resumable upload are multiples of 256 KB except the last one (resumable upload guide). */
    static final long CHUNK_ALIGNMENT = 256L * 1024L;
    static final String ANALYTICS_READONLY_SCOPE = "https://www.googleapis.com/auth/yt-analytics.readonly";
    static final String PUBLISH_FEATURE = "publish";
    static final String INSIGHTS_FEATURE = "insights";

    /** The first day the Analytics reads cover; YouTube was launched in 2005, so every video is inside the range. */
    static final String ANALYTICS_START_DATE = "2005-01-01";
    static final String WATCH_URL = "https://www.youtube.com/watch?v=";

    /**
     * Data API statistics fields and the metric each one feeds. A count the platform hides (for example likes of a channel
     * that turned them off) is absent from the response and stays unavailable.
     */
    static final Map<String, String> STATISTICS = Map.of("viewCount", Metrics.VIEWS, "likeCount", Metrics.LIKES,
            "commentCount", Metrics.COMMENTS);

    /** Analytics API metric names and the metric each one feeds; watch time arrives in minutes and is stored in seconds. */
    static final Map<String, String> ANALYTICS = Map.of("engagedViews", Metrics.ENGAGED_VIEWS,
            "averageViewPercentage", Metrics.AVERAGE_VIEW_PERCENTAGE, "estimatedMinutesWatched", Metrics.WATCH_TIME,
            "shares", Metrics.SHARES);

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
        return new PlatformCapabilities(true, true, true, true, true);
    }

    @Override
    public List<PlatformFeature> features() {
        return List.of(
                new PlatformFeature(ACCOUNT_FEATURE, Set.of(READONLY_SCOPE)),
                new PlatformFeature(PUBLISH_FEATURE, Set.of(UPLOAD_SCOPE, FORCE_SSL_SCOPE)),
                new PlatformFeature(INSIGHTS_FEATURE, Set.of(READONLY_SCOPE, ANALYTICS_READONLY_SCOPE)));
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
        VideoState state = video(accessToken, platformItemId);
        return state.visibility() == Visibility.PRIVATE ? PublicationStatus.PENDING : PublicationStatus.PUBLISHED;
    }

    @Override
    public Optional<StepwisePublisher> stepwise() {
        return Optional.of(this);
    }

    /**
     * Starts a resumable upload of a video (videos.insert, uploadType=resumable). Quota: 100 units and one upload of the
     * daily cap, charged by the caller before this call. The snippet carries the post's title, description and tags; the
     * status carries the visibility and the made-for-kids declaration explicitly, and the publish time when one is set.
     * Documented 2026-11-08: a publish time is honoured only for a private video that was never published
     * (https://developers.google.com/youtube/v3/docs/videos). No categoryId is sent: the reference does not mark it required
     * for videos.insert, which is an assumption to confirm in the test upload (task 1.2).
     */
    @Override
    public String startUpload(String accessToken, UploadStart start) {
        HttpTransport.Answer answer = raw("POST", endpoint(UPLOAD_ENDPOINT, "uploadType", "resumable", "part",
                        "snippet,status"), accessToken,
                Map.of("Content-Type", "application/json; charset=UTF-8",
                        "X-Upload-Content-Length", Long.toString(start.sizeBytes()),
                        "X-Upload-Content-Type", start.contentType()),
                uploadBody(start).getBytes(StandardCharsets.UTF_8), "the upload start");
        if (answer.status() / 100 != 2 || answer.location().isEmpty()) {
            throw failureOf(answer, "the upload start");
        }
        return answer.location().get();
    }

    /**
     * Sends one chunk at its offset. Quota: none; the chunks belong to the videos.insert call that started the session.
     * A 308 answer names the bytes the platform holds in its Range header; a 200 or 201 answer completes the upload.
     */
    @Override
    public UploadProgress sendChunk(String accessToken, String sessionLocation, long offset, long total,
                                    String contentType, byte[] chunk) {
        if (chunk.length == 0 || offset < 0 || offset + chunk.length > total) {
            throw new IllegalArgumentException("the chunk lies outside the upload");
        }
        long last = offset + chunk.length - 1;
        HttpTransport.Answer answer = raw("PUT", sessionLocation, accessToken, Map.of("Content-Type", contentType,
                "Content-Range", "bytes " + offset + "-" + last + "/" + total), chunk, "the upload of bytes");
        return progressOf(answer, total, "the upload of bytes");
    }

    /**
     * Asks the platform how many bytes it holds of the session, without sending any. Quota: none.
     * An expired session answers 404 and is reported as {@link UploadSessionExpired}.
     */
    @Override
    public UploadProgress uploadStatus(String accessToken, String sessionLocation, long total) {
        HttpTransport.Answer answer = raw("PUT", sessionLocation, accessToken, Map.of("Content-Length", "0",
                "Content-Range", "bytes */" + total), new byte[0], "the upload status");
        return progressOf(answer, total, "the upload status");
    }

    /**
     * Adds a video to a playlist (playlistItems.insert). Quota: 50 units, charged by the caller. A 409 answer means the
     * video is already in the playlist, which is the state a retry wants, so it is not a failure.
     */
    @Override
    public void addToPlaylist(String accessToken, String playlistId, String platformItemId) {
        String body = "{\"snippet\":{\"playlistId\":" + jsonString(playlistId) + ",\"resourceId\":{\"kind\":\"youtube#video\","
                + "\"videoId\":" + jsonString(platformItemId) + "}}}";
        HttpTransport.Answer answer = raw("POST", endpoint(PLAYLIST_ITEMS_ENDPOINT, "part", "snippet"), accessToken,
                Map.of("Content-Type", "application/json; charset=UTF-8"), body.getBytes(StandardCharsets.UTF_8),
                "the playlist insert");
        if (answer.status() == 409 || answer.status() / 100 == 2) {
            return;
        }
        throw failureOf(answer, "the playlist insert");
    }

    /**
     * The privacy, publish time, made-for-kids value and processing state of a video (videos.list, part status and
     * processingDetails). Quota: 1 unit, charged by the caller. A processing status of succeeded means processed.
     */
    @Override
    public VideoState video(String accessToken, String platformItemId) {
        Map<String, Object> document = parse(send("GET", endpoint(VIDEOS_ENDPOINT, "part",
                "status,processingDetails", "id", platformItemId), null, accessToken, "the video read"));
        for (Map<?, ?> video : objects(document.get("items"))) {
            if (!platformItemId.equals(text(video, "id"))) {
                continue;
            }
            Map<?, ?> status = mapOf(video.get("status"));
            Map<?, ?> processing = mapOf(video.get("processingDetails"));
            Boolean madeForKids = status.get("selfDeclaredMadeForKids") instanceof Boolean value ? value : null;
            return new VideoState(visibilityOf(text(status, "privacyStatus")), madeForKids,
                    instantOf(text(status, "publishAt")), text(processing, "processingStatus"));
        }
        throw new PlatformException(PlatformException.Kind.REJECTED, "YouTube has no such video.", null);
    }

    /**
     * Sets the custom thumbnail of a processed video (thumbnails.set, uploadType=media). Quota: 50 units, charged by the
     * caller. Thumbnails must be JPEG or PNG and at most 50 MB (checked 2026-11-08).
     */
    @Override
    public void setThumbnail(String accessToken, String platformItemId, String contentType, byte[] image) {
        HttpTransport.Answer answer = raw("POST", endpoint(THUMBNAIL_ENDPOINT, "videoId", platformItemId,
                "uploadType", "media"), accessToken, Map.of("Content-Type", contentType), image, "the thumbnail");
        if (answer.status() / 100 != 2) {
            throw failureOf(answer, "the thumbnail");
        }
    }

    @Override
    public void delete(String accessToken, String platformItemId) {
        throw new Unsupported("deleting a YouTube video");
    }

    /**
     * The public and unlisted videos of the channel, newest first, at most {@code limit}. Private videos are skipped.
     *
     * <p>Quota (determine_quota_cost, checked 2026-11-08): channels.list, playlistItems.list and videos.list each cost 1
     * unit per call. This method makes one channels.list call, one playlistItems.list call per page of 50 uploads read
     * (stopping at the limit), and one videos.list call per 50 listed videos for the duration that decides the format.
     *
     * <p>Format: the Data API has no Shorts flag; see {@link YouTubeFormat} for the duration rule. The description is the
     * snippet description of the upload.
     */
    @Override
    public List<PublishedItem> listPublished(String accessToken, int limit) {
        String uploads = uploadsPlaylistId(accessToken);
        if (uploads == null || limit <= 0) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        String pageToken = null;
        do {
            Map<String, Object> page = parse(send("GET", endpoint(PLAYLIST_ITEMS_ENDPOINT,
                    "part", "snippet,contentDetails,status", "playlistId", uploads, "maxResults", "50",
                    "pageToken", pageToken), null, accessToken, "the listing of the uploads"));
            for (Map<?, ?> item : objects(page.get("items"))) {
                Entry entry = entryOf(item);
                if (entry != null) {
                    entries.add(entry);
                }
                if (entries.size() >= limit) {
                    break;
                }
            }
            pageToken = nextPageToken(page);
        } while (pageToken != null && entries.size() < limit);
        List<Entry> chosen = entries.subList(0, Math.min(limit, entries.size()));
        Map<String, String> durations = durationsOf(accessToken, chosen.stream().map(Entry::videoId).toList());
        List<PublishedItem> items = new ArrayList<>();
        for (Entry entry : chosen) {
            items.add(new PublishedItem(entry.videoId(), WATCH_URL + entry.videoId(), entry.title(),
                    entry.publishedAt(), YouTubeFormat.of(durations.get(entry.videoId())), entry.description()));
        }
        return List.copyOf(items);
    }

    /**
     * The video identifiers a playlist of the channel contains, read from every page. Quota: one playlistItems.list call
     * (1 unit) per page of 50 items.
     */
    @Override
    public Set<String> playlistItemIds(String accessToken, String playlistId) {
        Set<String> members = new HashSet<>();
        String pageToken = null;
        do {
            Map<String, Object> page = parse(send("GET", endpoint(PLAYLIST_ITEMS_ENDPOINT,
                    "part", "contentDetails", "playlistId", playlistId, "maxResults", "50", "pageToken", pageToken),
                    null, accessToken, "the playlist read"));
            for (Map<?, ?> item : objects(page.get("items"))) {
                String videoId = text(mapOf(item.get("contentDetails")), "videoId");
                if (!videoId.isBlank()) {
                    members.add(videoId);
                }
            }
            pageToken = nextPageToken(page);
        } while (pageToken != null);
        return Set.copyOf(members);
    }

    @Override
    public int metricsBatchSize() {
        return YOUTUBE_BATCH_SIZE;
    }

    /**
     * The requested metrics of up to 50 videos. Views, likes and comments come from one videos.list call with the
     * statistics part (1 unit); engaged views, average view percentage, watch time and shares come from one Analytics
     * reports.query call per batch, with a per-video dimension. The Analytics reference checked 2026-11-08 states no quota
     * cost, so none is assumed here. Analytics has no hour dimension: per-hour figures come from Menura's own snapshots.
     *
     * <p>A call that fails fails the whole batch. A metric or video missing from a response is left out of the result.
     */
    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                     Set<String> metrics) {
        if (platformItemIds.size() > YOUTUBE_BATCH_SIZE) {
            throw new IllegalArgumentException("a metrics read takes at most " + YOUTUBE_BATCH_SIZE + " items");
        }
        if (platformItemIds.isEmpty()) {
            return Map.of();
        }
        Set<String> requested = new HashSet<>(platformItemIds);
        Map<String, Map<String, Long>> out = new HashMap<>();
        if (STATISTICS.values().stream().anyMatch(metrics::contains)) {
            Map<String, Object> document = parse(send("GET", endpoint(VIDEOS_ENDPOINT,
                    "part", "statistics", "id", String.join(",", platformItemIds)), null, accessToken,
                    "the statistics read"));
            for (Map<?, ?> video : objects(document.get("items"))) {
                String id = text(video, "id");
                if (!requested.contains(id)) {
                    continue;
                }
                Map<String, Long> values = out.computeIfAbsent(id, key -> new HashMap<>());
                Map<?, ?> statistics = mapOf(video.get("statistics"));
                for (Map.Entry<String, String> field : STATISTICS.entrySet()) {
                    Long value = longOf(statistics.get(field.getKey()));
                    if (value != null && metrics.contains(field.getValue())) {
                        values.put(field.getValue(), value);
                    }
                }
            }
        }
        List<String> analytics = ANALYTICS.entrySet().stream().filter(field -> metrics.contains(field.getValue()))
                .map(Map.Entry::getKey).sorted().toList();
        if (!analytics.isEmpty()) {
            String today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
            Map<String, Object> report = parse(send("GET", endpoint(ANALYTICS_ENDPOINT,
                    "ids", "channel==MINE", "startDate", ANALYTICS_START_DATE, "endDate", today,
                    "dimensions", "video", "metrics", String.join(",", analytics),
                    "filters", "video==" + String.join(",", platformItemIds)), null, accessToken,
                    "the analytics read"));
            List<String> columns = objects(report.get("columnHeaders")).stream().map(header -> text(header, "name"))
                    .toList();
            int videoColumn = columns.indexOf("video");
            if (report.get("rows") instanceof List<?> rows && videoColumn >= 0) {
                for (Object rowValue : rows) {
                    if (rowValue instanceof List<?> row && videoColumn < row.size()
                            && row.get(videoColumn) instanceof String id && requested.contains(id)) {
                        Map<String, Long> values = out.computeIfAbsent(id, key -> new HashMap<>());
                        for (Map.Entry<String, String> field : ANALYTICS.entrySet()) {
                            int column = columns.indexOf(field.getKey());
                            if (metrics.contains(field.getValue()) && column >= 0 && column < row.size()
                                    && row.get(column) instanceof Number number) {
                                values.put(field.getValue(), toMetric(field.getValue(), number));
                            }
                        }
                    }
                }
            }
        }
        return out;
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

    /** A raw request (upload protocol, playlist insert, thumbnail) to a Google host from {@link #HOSTS} over HTTPS. */
    private HttpTransport.Answer raw(String method, String endpoint, String bearer, Map<String, String> headers,
                                     byte[] body, String what) {
        URI uri = URI.create(endpoint);
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
            throw new IllegalStateException("the YouTube adapter may only reach its Google endpoints");
        }
        try {
            return http.call(method, uri, bearer, headers, body);
        } catch (IOException failure) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE,
                    "YouTube could not be reached for " + what + ".", null);
        } catch (UnsupportedOperationException notRaw) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE,
                    "This build cannot send " + what + " to YouTube.", null);
        }
    }

    /** The progress of an upload from its answer: 308 names the confirmed bytes, 200 or 201 completes it. */
    private static UploadProgress progressOf(HttpTransport.Answer answer, long total, String what) {
        if (answer.status() == 404) {
            throw new UploadSessionExpired();
        }
        if (answer.status() == 200 || answer.status() == 201) {
            String id = text(parseOrEmpty(answer.body()), "id");
            if (id.isBlank()) {
                throw new PlatformException(PlatformException.Kind.UNAVAILABLE,
                        "YouTube completed " + what + " without a video.", null);
            }
            return new UploadProgress(total, id, WATCH_URL + id);
        }
        if (answer.status() == 308) {
            return new UploadProgress(confirmedBytes(answer.range()), null, null);
        }
        throw failureOf(answer, what);
    }

    /** The bytes held by the platform: the Range header is zero-based and inclusive; no header means none. */
    private static long confirmedBytes(Optional<String> range) {
        if (range.isEmpty()) {
            return 0L;
        }
        String value = range.get();
        int dash = value.lastIndexOf('-');
        if (!value.startsWith("bytes=") || dash < 0) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE, "YouTube answered an upload range that cannot be read.",
                    null);
        }
        try {
            return Long.parseLong(value.substring(dash + 1)) + 1;
        } catch (NumberFormatException notNumber) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE, "YouTube answered an upload range that cannot be read.",
                    null);
        }
    }

    private static PlatformException failureOf(HttpTransport.Answer answer, String what) {
        return failureOf(new HttpTransport.Reply(answer.status(), answer.body() == null ? "" : answer.body(),
                answer.retryAfter()), what);
    }

    /** The upload's snippet and status, with visibility, made-for-kids and publish time always present. */
    private static String uploadBody(UploadStart start) {
        StringBuilder tags = new StringBuilder("[");
        for (String tag : start.tags()) {
            if (tags.length() > 1) {
                tags.append(',');
            }
            tags.append(jsonString(tag));
        }
        tags.append(']');
        String publishAt = start.publishAt() == null ? "" : ",\"publishAt\":" + jsonString(start.publishAt().toString());
        return "{\"snippet\":{\"title\":" + jsonString(start.title()) + ",\"description\":" + jsonString(start.description())
                + ",\"tags\":" + tags + "},\"status\":{\"privacyStatus\":" + jsonString(privacyOf(start.visibility()))
                + ",\"selfDeclaredMadeForKids\":" + start.madeForKids() + publishAt + "}}";
    }

    private static String privacyOf(Visibility visibility) {
        return visibility.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Visibility visibilityOf(String privacy) {
        return switch (privacy) {
            case "public" -> Visibility.PUBLIC;
            case "unlisted" -> Visibility.UNLISTED;
            case "private" -> Visibility.PRIVATE;
            default -> null;
        };
    }

    /** A JSON string literal with the characters JSON requires escaped. */
    static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static Map<String, Object> parseOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return parse(json);
        } catch (PlatformException notJson) {
            return Map.of();
        }
    }

    private static PlatformException failureOf(HttpTransport.Reply reply, String what) {
        String body = reply.body() == null ? "" : reply.body();
        if (reply.status() == 429 || (reply.status() == 403 && (body.contains("quotaExceeded")
                || body.contains("rateLimitExceeded")))) {
            return new PlatformException(PlatformException.Kind.RATE_LIMITED,
                    "YouTube is rate limiting " + what + ".", retryAfter(reply));
        }
        if (reply.status() == 400 || reply.status() == 401
                || (reply.status() == 403 && body.contains("insufficientPermissions"))) {
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

    /** The uploads playlist of the channel of the token, or null when the account has no channel. Quota: 1 unit. */
    private String uploadsPlaylistId(String accessToken) {
        Map<String, Object> document = parse(send("GET", endpoint(CHANNELS_ENDPOINT,
                "part", "contentDetails", "mine", "true"), null, accessToken, "the channel lookup"));
        for (Map<?, ?> channel : objects(document.get("items"))) {
            String uploads = text(mapOf(mapOf(channel.get("contentDetails")).get("relatedPlaylists")), "uploads");
            if (!uploads.isBlank()) {
                return uploads;
            }
        }
        return null;
    }

    /** The durations of the given videos, in batches of the platform limit. Quota: 1 unit per batch. */
    private Map<String, String> durationsOf(String accessToken, List<String> videoIds) {
        Map<String, String> durations = new HashMap<>();
        for (int from = 0; from < videoIds.size(); from += YOUTUBE_BATCH_SIZE) {
            List<String> batch = videoIds.subList(from, Math.min(videoIds.size(), from + YOUTUBE_BATCH_SIZE));
            Map<String, Object> document = parse(send("GET", endpoint(VIDEOS_ENDPOINT,
                    "part", "contentDetails", "id", String.join(",", batch)), null, accessToken, "the video details"));
            for (Map<?, ?> video : objects(document.get("items"))) {
                String id = text(video, "id");
                if (!id.isBlank()) {
                    durations.put(id, text(mapOf(video.get("contentDetails")), "duration"));
                }
            }
        }
        return durations;
    }

    /** One upload of the channel: a public or unlisted video with its title, description and publish time. */
    private record Entry(String videoId, String title, String description, Instant publishedAt) {
    }

    /** The upload as an entry, or null for a private video or an item without an identifier or a publish time. */
    private static Entry entryOf(Map<?, ?> item) {
        String privacy = text(mapOf(item.get("status")), "privacyStatus");
        if (!"public".equals(privacy) && !"unlisted".equals(privacy)) {
            return null;
        }
        Map<?, ?> details = mapOf(item.get("contentDetails"));
        Map<?, ?> snippet = mapOf(item.get("snippet"));
        String videoId = text(details, "videoId");
        String when = text(details, "videoPublishedAt");
        Instant publishedAt = instantOf(when.isBlank() ? text(snippet, "publishedAt") : when);
        if (videoId.isBlank() || publishedAt == null) {
            return null;
        }
        return new Entry(videoId, text(snippet, "title"), text(snippet, "description"), publishedAt);
    }

    private static Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException notNumber) {
                return null;
            }
        }
        return null;
    }

    /** Analytics values as stored: percentages and counts rounded to whole numbers, watch time from minutes to seconds. */
    private static long toMetric(String metric, Number value) {
        if (Metrics.WATCH_TIME.equals(metric)) {
            return Math.round(value.doubleValue() * 60.0);
        }
        return Math.round(value.doubleValue());
    }

    private static Instant instantOf(String text) {
        if (text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException notInstant) {
            return null;
        }
    }

    private static String endpoint(String base, String... pairs) {
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] == null) {
                continue;
            }
            query.append(query.isEmpty() ? '?' : '&')
                    .append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return base + query;
    }

    private static Map<?, ?> mapOf(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Map<?, ?> map, String key) {
        return map.get(key) instanceof String value ? value : "";
    }

    private static List<Map<?, ?>> objects(Object value) {
        List<Map<?, ?>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> map) {
                    out.add(map);
                }
            }
        }
        return out;
    }

    private static String nextPageToken(Map<?, ?> page) {
        String token = text(page, "nextPageToken");
        return token.isBlank() ? null : token;
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
