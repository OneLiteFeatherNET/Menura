package net.onelitefeather.menura.core.channel.youtube;

import com.nimbusds.jose.util.JSONObjectUtils;
import net.onelitefeather.menura.core.channel.HttpTransport;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A YouTube that runs on this machine. It answers the HTTP requests that {@link YouTubeAdapter} sends, so the adapter's own
 * code (request shapes, response parsing, error classification) runs unchanged against generated data. The transport only
 * answers: nothing is sent anywhere, and a URI outside the Google hosts of the adapter fails with an {@link IOException}.
 *
 * <p>The data is a function of the seed and the injected clock: a back catalogue of {@link #CATALOGUE_SIZE} videos
 * published over the {@link #CATALOGUE_DAYS} days before the simulator started, statistics that grow with age, three
 * owned playlists, and the uploads of this instance. An upload that carries a publish time stays private until the
 * clock reaches it, as YouTube does. Uploads, playlist inserts and thumbnails live in this instance only, so they are
 * not shared between processes.
 */
final class SimulatedYouTube implements HttpTransport {

    static final String CHANNEL_ID = "UCsimulatedchannel00001";
    static final String CHANNEL_TITLE = "Simulated channel";
    static final String UPLOADS_PLAYLIST_ID = "UUsimulatedchannel00001";
    static final String ACCESS_TOKEN = "simulated-access-token";
    static final String REFRESH_TOKEN = "simulated-refresh-token";
    static final String AUTHORIZATION_CODE = "simulated-code";
    static final long TOKEN_LIFETIME_SECONDS = 3600L;
    static final int CATALOGUE_SIZE = 24;
    static final int CATALOGUE_DAYS = 90;
    static final List<String> PLAYLIST_IDS = List.of("PLsimulated-series", "PLsimulated-shorts", "PLsimulated-archive");
    /** Every scope the adapter can request; the simulated grant carries all of them. */
    static final Set<String> SCOPES = Set.of(YouTubeAdapter.READONLY_SCOPE, YouTubeAdapter.UPLOAD_SCOPE,
            YouTubeAdapter.FORCE_SSL_SCOPE, YouTubeAdapter.ANALYTICS_READONLY_SCOPE);

    private static final int PAGE_SIZE = 50;
    private static final int ID_LENGTH = 11;
    private static final String ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final Duration UPLOAD_LENGTH = Duration.ofMinutes(4).plusSeconds(10);
    private static final Set<String> ANALYTICS_METRICS = Set.of("engagedViews", "averageViewPercentage",
            "estimatedMinutesWatched", "shares");
    private static final Set<String> INTEGER_METRICS = Set.of("engagedViews", "shares");
    private static final Set<String> UPLOAD_PARTS = Set.of("snippet", "status", "processingDetails");
    private static final Pattern CHUNK_RANGE = Pattern.compile("bytes (\\d{1,18})-(\\d{1,18})/(\\d{1,18})");

    // Request paths. Matched together with the method; the host is checked before any of them.
    private static final String TOKEN_PATH = "/token";
    private static final String REVOKE_PATH = "/revoke";
    private static final String CHANNELS_PATH = "/youtube/v3/channels";
    private static final String PLAYLISTS_PATH = "/youtube/v3/playlists";
    private static final String PLAYLIST_ITEMS_PATH = "/youtube/v3/playlistItems";
    private static final String VIDEOS_PATH = "/youtube/v3/videos";
    private static final String UPLOADS_PATH = "/upload/youtube/v3/videos";
    private static final String THUMBNAILS_PATH = "/upload/youtube/v3/thumbnails/set";
    private static final String REPORTS_PATH = "/v2/reports";

    private final Clock clock;
    private final long seed;
    /** The start of the day the simulator was created: the catalogue ages from here, so its statistics grow with the clock. */
    private final Instant anchor;
    private final Set<String> revoked = new HashSet<>();
    private final Map<String, Upload> sessions = new HashMap<>();
    private final Map<String, Video> uploaded = new LinkedHashMap<>();
    private final Map<String, Set<String>> playlistInserts = new HashMap<>();
    private final Set<String> thumbnails = new HashSet<>();
    private int uploadCount;

    SimulatedYouTube(Clock clock, long seed) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.seed = seed;
        this.anchor = clock.instant().truncatedTo(ChronoUnit.DAYS);
    }

    @Override
    public synchronized Reply send(String method, URI uri, String bearerToken, String formBody) throws IOException {
        requireGoogleHost(uri);
        String path = uri.getPath();
        Answer answer;
        if ("POST".equals(method) && TOKEN_PATH.equals(path)) {
            answer = tokenRequest(decode(formBody));
        } else if ("POST".equals(method) && REVOKE_PATH.equals(path)) {
            answer = revoke(decode(formBody));
        } else if (!"GET".equals(method)) {
            answer = error(405, "methodNotAllowed");
        } else if (!authorized(bearerToken)) {
            answer = error(401, "authError");
        } else {
            answer = read(path, decode(uri.getRawQuery()));
        }
        return new Reply(answer.status(), answer.body(), answer.retryAfter());
    }

    @Override
    public synchronized Answer call(String method, URI uri, String bearerToken, Map<String, String> headers, byte[] body)
            throws IOException {
        requireGoogleHost(uri);
        if (!authorized(bearerToken)) {
            return error(401, "authError");
        }
        String path = uri.getPath();
        String bodyText = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        if ("POST".equals(method) && UPLOADS_PATH.equals(path)) {
            return startUpload(headers, bodyText);
        }
        if ("PUT".equals(method) && UPLOADS_PATH.equals(path)) {
            return uploadBytes(decode(uri.getRawQuery()), headers);
        }
        if ("POST".equals(method) && PLAYLIST_ITEMS_PATH.equals(path)) {
            return addToPlaylist(bodyText);
        }
        if ("POST".equals(method) && THUMBNAILS_PATH.equals(path)) {
            return setThumbnail(decode(uri.getRawQuery()));
        }
        return error(404, "notFound");
    }

    // ------------------------------------------------------------ OAuth

    private Answer tokenRequest(Map<String, String> form) {
        String grant = form.getOrDefault("grant_type", "");
        if ("authorization_code".equals(grant) && AUTHORIZATION_CODE.equals(form.get("code"))) {
            return issueTokens(true);
        }
        if ("refresh_token".equals(grant) && REFRESH_TOKEN.equals(form.get("refresh_token"))
                && !revoked.contains(REFRESH_TOKEN)) {
            return issueTokens(false);
        }
        return reply(400, obj("error", "invalid_grant"));
    }

    private Answer issueTokens(boolean withRefreshToken) {
        revoked.remove(ACCESS_TOKEN);
        if (withRefreshToken) {
            revoked.remove(REFRESH_TOKEN);
        }
        return reply(200, obj("access_token", ACCESS_TOKEN, "expires_in", TOKEN_LIFETIME_SECONDS,
                "scope", SCOPES.stream().sorted().collect(Collectors.joining(" ")), "token_type", "Bearer",
                "refresh_token", withRefreshToken ? REFRESH_TOKEN : null));
    }

    private Answer revoke(Map<String, String> form) {
        String token = form.get("token");
        boolean live = (ACCESS_TOKEN.equals(token) || REFRESH_TOKEN.equals(token)) && !revoked.contains(token);
        if (!live) {
            return reply(400, obj("error", "invalid_token"));
        }
        revoked.add(token);
        return reply(200, obj());
    }

    private boolean authorized(String bearerToken) {
        return ACCESS_TOKEN.equals(bearerToken) && !revoked.contains(ACCESS_TOKEN);
    }

    // ------------------------------------------------------------ reads

    private Answer read(String path, Map<String, String> query) {
        Instant now = clock.instant();
        return switch (path) {
            case CHANNELS_PATH -> channels(query);
            case PLAYLISTS_PATH -> ownedPlaylists();
            case PLAYLIST_ITEMS_PATH -> playlistItems(query, now);
            case VIDEOS_PATH -> videos(query, now);
            case REPORTS_PATH -> report(query, now);
            default -> error(404, "notFound");
        };
    }

    /** The channel of the token (mine=true) or the channel by identifier. */
    private Answer channels(Map<String, String> query) {
        boolean mine = "true".equals(query.get("mine"));
        boolean byId = CHANNEL_ID.equals(query.get("id"));
        List<Map<String, Object>> items = mine || byId ? List.of(channel()) : List.of();
        return reply(200, obj("kind", "youtube#channelListResponse", "items", items));
    }

    private static Map<String, Object> channel() {
        return obj("kind", "youtube#channel", "id", CHANNEL_ID, "snippet", obj("title", CHANNEL_TITLE),
                "contentDetails", obj("relatedPlaylists", obj("uploads", UPLOADS_PLAYLIST_ID)));
    }

    /** The playlists the channel owns (playlists.list with mine=true): all of them fit on one page. */
    private static Answer ownedPlaylists() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < PLAYLIST_IDS.size(); i++) {
            items.add(obj("kind", "youtube#playlist", "id", PLAYLIST_IDS.get(i),
                    "snippet", obj("title", "Simulated playlist " + (i + 1))));
        }
        return reply(200, obj("kind", "youtube#playlistListResponse", "items", items));
    }

    /** The uploads playlist (newest first) or one of the owned playlists, paged by offset. */
    private Answer playlistItems(Map<String, String> query, Instant now) {
        String playlistId = query.get("playlistId");
        List<Video> videos;
        if (UPLOADS_PLAYLIST_ID.equals(playlistId)) {
            videos = channelVideos(now);
        } else if (playlistId != null && PLAYLIST_IDS.contains(playlistId)) {
            videos = playlistMembers(playlistId, now).stream().map(id -> find(id, now)).flatMap(Optional::stream)
                    .toList();
        } else {
            return error(404, "playlistNotFound");
        }
        return page(query, videos.stream().map(video -> playlistItem(video, now)).toList());
    }

    /** The videos of the given identifiers that exist, with the parts the request names. */
    private Answer videos(Map<String, String> query, Instant now) {
        Set<String> parts = Set.copyOf(Arrays.asList(query.getOrDefault("part", "").split(",")));
        List<Map<String, Object>> items = new ArrayList<>();
        for (String id : query.getOrDefault("id", "").split(",")) {
            find(id, now).ifPresent(video -> items.add(videoResource(video, parts, now)));
        }
        return reply(200, obj("kind", "youtube#videoListResponse", "items", items));
    }

    /** The Analytics report of the videos named in the filter, one row per existing video. */
    private Answer report(Map<String, String> query, Instant now) {
        String filter = query.getOrDefault("filters", "");
        if (!"video".equals(query.get("dimensions")) || !filter.startsWith("video==")) {
            return error(400, "badRequest");
        }
        List<String> metrics = Arrays.asList(query.getOrDefault("metrics", "").split(","));
        if (!ANALYTICS_METRICS.containsAll(metrics)) {
            return error(400, "badRequest");
        }
        List<Map<String, Object>> headers = new ArrayList<>();
        headers.add(obj("name", "video", "columnType", "DIMENSION", "dataType", "STRING"));
        for (String metric : metrics) {
            headers.add(obj("name", metric, "columnType", "METRIC",
                    "dataType", INTEGER_METRICS.contains(metric) ? "INTEGER" : "FLOAT"));
        }
        List<List<Object>> rows = new ArrayList<>();
        for (String id : filter.substring("video==".length()).split(",")) {
            Optional<Video> video = find(id, now);
            if (video.isEmpty()) {
                continue;
            }
            Stats stats = statsOf(video.get(), now);
            List<Object> row = new ArrayList<>();
            row.add(id);
            for (String metric : metrics) {
                row.add(analyticsValue(metric, video.get(), stats));
            }
            rows.add(row);
        }
        return reply(200, obj("kind", "youtubeAnalytics#resultTable", "columnHeaders", headers, "rows", rows));
    }

    // ------------------------------------------------------------ writes

    /** Starts a resumable upload: the metadata is kept, the session location is answered in the Location header. */
    private Answer startUpload(Map<String, String> headers, String bodyText) {
        Map<String, Object> metadata;
        long size;
        try {
            metadata = JSONObjectUtils.parse(bodyText);
            size = Long.parseLong(headers.getOrDefault("X-Upload-Content-Length", "0"));
        } catch (ParseException | NumberFormatException invalid) {
            return error(400, "badRequest");
        }
        if (size <= 0) {
            return error(400, "badRequest");
        }
        uploadCount++;
        String session = "simulated-session-" + uploadCount;
        sessions.put(session, new Upload(uploadCount, metadata, size));
        String location = YouTubeAdapter.UPLOAD_ENDPOINT + "?uploadType=resumable&upload_id=" + session;
        return new Answer(200, "", Optional.of(location), Optional.empty(), Optional.empty());
    }

    /** One chunk, or a status query whose Content-Range names no range. The bytes themselves are only counted. */
    private Answer uploadBytes(Map<String, String> query, Map<String, String> headers) {
        Upload upload = sessions.get(query.get("upload_id"));
        if (upload == null) {
            return error(404, "uploadNotFound");
        }
        String contentRange = headers.getOrDefault("Content-Range", "");
        if (contentRange.startsWith("bytes */")) {
            return uploadAnswer(upload);
        }
        Matcher range = CHUNK_RANGE.matcher(contentRange);
        if (!range.matches()) {
            return error(400, "badRequest");
        }
        long start = Long.parseLong(range.group(1));
        long end = Long.parseLong(range.group(2));
        if (Long.parseLong(range.group(3)) != upload.total || end < start || end >= upload.total) {
            return error(400, "badRequest");
        }
        if (start != upload.received) {
            return uploadAnswer(upload);
        }
        upload.received = end + 1;
        if (upload.received == upload.total) {
            upload.video = completeUpload(upload);
        }
        return uploadAnswer(upload);
    }

    /** 308 with the confirmed bytes while the upload runs; 200 with the video once it is complete. */
    private Answer uploadAnswer(Upload upload) {
        if (upload.video == null) {
            Optional<String> range = upload.received == 0 ? Optional.empty()
                    : Optional.of("bytes=0-" + (upload.received - 1));
            return new Answer(308, "", Optional.empty(), range, Optional.empty());
        }
        return reply(200, videoResource(upload.video, UPLOAD_PARTS, clock.instant()));
    }

    /** The video of a completed upload: a timed upload is private until its publish time, as the platform does. */
    private Video completeUpload(Upload upload) {
        Map<?, ?> snippet = mapOf(upload.metadata.get("snippet"));
        Map<?, ?> status = mapOf(upload.metadata.get("status"));
        Instant publishAt = instantOf(text(status, "publishAt"));
        String privacy = text(status, "privacyStatus");
        Video video = new Video(identifier(random("upload:" + upload.number)), text(snippet, "title"),
                text(snippet, "description"), publishAt != null ? publishAt : clock.instant(), UPLOAD_LENGTH,
                privacy.isBlank() ? "private" : privacy, publishAt, null);
        uploaded.put(video.id(), video);
        return video;
    }

    private Answer addToPlaylist(String bodyText) {
        Map<String, Object> body;
        try {
            body = JSONObjectUtils.parse(bodyText);
        } catch (ParseException notJson) {
            return error(400, "badRequest");
        }
        Map<?, ?> snippet = mapOf(body.get("snippet"));
        String playlistId = text(snippet, "playlistId");
        String videoId = text(mapOf(snippet.get("resourceId")), "videoId");
        Instant now = clock.instant();
        if (!PLAYLIST_IDS.contains(playlistId)) {
            return error(404, "playlistNotFound");
        }
        if (find(videoId, now).isEmpty()) {
            return error(404, "videoNotFound");
        }
        if (playlistMembers(playlistId, now).contains(videoId)) {
            return error(409, "videoAlreadyInPlaylist");
        }
        playlistInserts.computeIfAbsent(playlistId, key -> new LinkedHashSet<>()).add(videoId);
        return reply(200, obj("kind", "youtube#playlistItem", "snippet", obj("playlistId", playlistId,
                "resourceId", obj("kind", "youtube#video", "videoId", videoId))));
    }

    private Answer setThumbnail(Map<String, String> query) {
        String videoId = query.get("videoId");
        if (videoId == null || find(videoId, clock.instant()).isEmpty()) {
            return error(404, "videoNotFound");
        }
        thumbnails.add(videoId);
        return reply(200, obj("kind", "youtube#thumbnailSetResponse"));
    }

    // ------------------------------------------------------------ generated data

    /** The back catalogue: every video is generated from its index, the seed and the anchor of the simulator. */
    private List<Video> catalogue() {
        List<Video> videos = new ArrayList<>(CATALOGUE_SIZE);
        for (int index = 0; index < CATALOGUE_SIZE; index++) {
            videos.add(catalogueVideo(index));
        }
        return videos;
    }

    /** Every third video is a Short of at most 60 seconds; the others run longer than three minutes. */
    private Video catalogueVideo(int index) {
        Random random = random("catalogue:" + index);
        boolean isShort = index % 3 == 0;
        Duration length = isShort ? Duration.ofSeconds(15 + random.nextInt(46))
                : Duration.ofSeconds(240 + random.nextInt(900));
        int ageDays = 1 + index * (CATALOGUE_DAYS - 2) / (CATALOGUE_SIZE - 1);
        Instant publishedAt = anchor.minus(Duration.ofDays(ageDays)).minus(Duration.ofHours(random.nextInt(12)));
        String privacy = switch (index % 6) {
            case 2 -> "unlisted";
            case 5 -> "private";
            default -> "public";
        };
        String playlistId = index % 2 == 0 ? PLAYLIST_IDS.get(index % 3) : null;
        String title = (isShort ? "Simulated short " : "Simulated video ") + String.format(Locale.ROOT, "%02d", index + 1);
        return new Video(identifier(random("id:" + index)), title, "Generated for the simulated channel. #simulated",
                publishedAt, length, privacy, null, playlistId);
    }

    /** Uploads and catalogue, newest first: the uploads playlist of the channel. */
    private List<Video> channelVideos(Instant now) {
        List<Video> videos = new ArrayList<>(catalogue());
        videos.addAll(uploaded.values());
        videos.sort(Comparator.comparing(Video::publishedAt).reversed());
        return videos;
    }

    /** The identifiers in an owned playlist: the catalogue members first, then the inserts of this instance. */
    private Set<String> playlistMembers(String playlistId, Instant now) {
        Set<String> ids = new LinkedHashSet<>();
        for (Video video : catalogue()) {
            if (playlistId.equals(video.playlistId())) {
                ids.add(video.id());
            }
        }
        ids.addAll(playlistInserts.getOrDefault(playlistId, Set.of()));
        return ids;
    }

    private Optional<Video> find(String id, Instant now) {
        Optional<Video> catalogued = catalogue().stream().filter(video -> video.id().equals(id)).findFirst();
        return catalogued.isPresent() ? catalogued : Optional.ofNullable(uploaded.get(id));
    }

    /** Views grow with the age of the video (none before it is published); likes and comments follow the views. */
    private Stats statsOf(Video video, Instant now) {
        if (now.isBefore(video.publishedAt())) {
            return new Stats(0L, 0L, 0L);
        }
        Random random = random("stats:" + video.id());
        long days = Duration.between(video.publishedAt(), now).toDays() + 1;
        long views = (20 + random.nextInt(180)) * days;
        long likes = views * (3 + random.nextInt(5)) / 100;
        long comments = views * (1 + random.nextInt(3)) / 1000;
        return new Stats(views, likes, comments);
    }

    private Object analyticsValue(String metric, Video video, Stats stats) {
        double averagePercentage = Math.round((30 + random("analytics:" + video.id()).nextDouble() * 40) * 100) / 100.0;
        return switch (metric) {
            case "engagedViews" -> stats.views() * 7 / 10;
            case "averageViewPercentage" -> averagePercentage;
            case "estimatedMinutesWatched" ->
                    Math.round(stats.views() * video.length().getSeconds() * averagePercentage / 60.0) / 100.0;
            default -> stats.likes() / 10;
        };
    }

    private static String privacyAt(Video video, Instant now) {
        if (video.publishAt() == null) {
            return video.privacy();
        }
        return now.isBefore(video.publishAt()) ? "private" : "public";
    }

    private Map<String, Object> videoResource(Video video, Set<String> parts, Instant now) {
        Map<String, Object> item = obj("kind", "youtube#video", "id", video.id());
        if (parts.contains("snippet")) {
            item.put("snippet", obj("title", video.title(), "description", video.description(),
                    "publishedAt", video.publishedAt().toString()));
        }
        if (parts.contains("contentDetails")) {
            item.put("contentDetails", obj("duration", video.length().toString()));
        }
        if (parts.contains("statistics")) {
            Stats stats = statsOf(video, now);
            item.put("statistics", obj("viewCount", String.valueOf(stats.views()),
                    "likeCount", String.valueOf(stats.likes()), "commentCount", String.valueOf(stats.comments())));
        }
        if (parts.contains("status")) {
            item.put("status", obj("privacyStatus", privacyAt(video, now), "selfDeclaredMadeForKids", false,
                    "publishAt", video.publishAt() == null ? null : video.publishAt().toString()));
        }
        if (parts.contains("processingDetails")) {
            item.put("processingDetails", obj("processingStatus", "succeeded"));
        }
        return item;
    }

    private static Map<String, Object> playlistItem(Video video, Instant now) {
        return obj("kind", "youtube#playlistItem",
                "snippet", obj("title", video.title(), "description", video.description(),
                        "publishedAt", video.publishedAt().toString()),
                "contentDetails", obj("videoId", video.id(), "videoPublishedAt", video.publishedAt().toString()),
                "status", obj("privacyStatus", privacyAt(video, now)));
    }

    /** One page of a list: the token is the offset of the next item. */
    private static Answer page(Map<String, String> query, List<Map<String, Object>> items) {
        int offset;
        try {
            offset = Integer.parseInt(query.getOrDefault("pageToken", "0"));
        } catch (NumberFormatException invalid) {
            return error(400, "invalidPageToken");
        }
        if (offset < 0) {
            return error(400, "invalidPageToken");
        }
        int start = Math.min(offset, items.size());
        int end = Math.min(items.size(), start + PAGE_SIZE);
        Map<String, Object> body = obj("kind", "youtube#playlistItemListResponse",
                "pageInfo", obj("totalResults", items.size(), "resultsPerPage", PAGE_SIZE),
                "items", items.subList(start, end));
        if (end < items.size()) {
            body.put("nextPageToken", String.valueOf(end));
        }
        return reply(200, body);
    }

    /** A random generator per item: the seed and the key decide the sequence, so every item is repeatable. */
    private Random random(String key) {
        return new Random(seed ^ (key.hashCode() * 0x9E3779B97F4A7C15L));
    }

    private static String identifier(Random random) {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }

    // ------------------------------------------------------------ helpers

    private static void requireGoogleHost(URI uri) throws IOException {
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || !YouTubeAdapter.HOSTS.contains(host)) {
            throw new IOException("the simulated YouTube answers only Google hosts over HTTPS, not " + uri);
        }
    }

    private static Answer reply(int status, Map<String, ?> body) {
        return new Answer(status, JSONObjectUtils.toJSONString(body), Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    private static Answer error(int status, String reason) {
        return reply(status, obj("error", obj("code", status, "message", reason,
                "errors", List.of(obj("reason", reason)))));
    }

    /** A JSON object in insertion order; a null value leaves its key out. */
    private static Map<String, Object> obj(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                out.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }

    /** The parameters of a query string or a form body; a parameter without a value maps to the empty string. */
    static Map<String, String> decode(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static Map<?, ?> mapOf(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Map<?, ?> map, String key) {
        return map.get(key) instanceof String value ? value : "";
    }

    private static Instant instantOf(String text) {
        if (text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException notInstant) {
            return null;
        }
    }

    private record Video(String id, String title, String description, Instant publishedAt, Duration length,
                         String privacy, Instant publishAt, String playlistId) {
    }

    private record Stats(long views, long likes, long comments) {
    }

    /** An upload in progress: the metadata sent at its start, the bytes the platform holds, and the video once complete. */
    private static final class Upload {

        private final int number;
        private final Map<String, Object> metadata;
        private final long total;
        private long received;
        private Video video;

        Upload(int number, Map<String, Object> metadata, long total) {
            this.number = number;
            this.metadata = metadata;
            this.total = total;
        }
    }
}
