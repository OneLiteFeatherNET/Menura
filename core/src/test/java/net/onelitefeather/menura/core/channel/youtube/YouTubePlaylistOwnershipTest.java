package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.channel.HttpTransport;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The playlists the channel owns, read through playlists.list with mine=true from a recorded transport. No test reaches the
 * platform and no token is real.
 */
class YouTubePlaylistOwnershipTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final String TOKEN = "token-for-tests";
    private static final String PLAYLISTS = "/youtube/v3/playlists";

    @Test
    void theOwnedPlaylistsAreReadFromEveryPageOfTheChannel() {
        RecordedYouTube http = new RecordedYouTube()
                .when("GET", PLAYLISTS, query -> "true".equals(query.get("mine")) && !query.containsKey("pageToken"),
                        new HttpTransport.Reply(200, RecordedYouTube.json("playlists-page-1.json")))
                .when("GET", PLAYLISTS, query -> "PAGE2".equals(query.get("pageToken")),
                        new HttpTransport.Reply(200, RecordedYouTube.json("playlists-page-2.json")));

        Set<String> owned = adapter(http).ownedPlaylistIds(TOKEN);

        assertEquals(Set.of("PL-own-series", "PL-own-archive"), owned,
                "the playlists of every page of the channel are the owned ones");
    }

    @Test
    void theOwnedPlaylistsRequestTheChannelsOwnPlaylistsOnly() {
        RecordedYouTube http = new RecordedYouTube()
                .on("GET", PLAYLISTS, new HttpTransport.Reply(200, "{\"items\":[]}"));

        assertEquals(Set.of(), adapter(http).ownedPlaylistIds(TOKEN), "a channel without playlists owns none");
        assertTrue(http.sent().get(0).uri().getRawQuery().contains("mine=true"),
                "the request asks for the playlists of the authorized channel, not any playlist by identifier");
    }

    private static YouTubeAdapter adapter(HttpTransport http) {
        return new YouTubeAdapter(Clock.fixed(NOW, ZoneOffset.UTC),
                new YouTubeAdapter.Settings("client", "secret", "https://menura.example/callback"), http);
    }
}
