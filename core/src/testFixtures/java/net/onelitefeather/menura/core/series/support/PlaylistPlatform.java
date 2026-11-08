package net.onelitefeather.menura.core.series.support;

import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformFeature;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A platform adapter whose playlist membership the test sets. With {@code supportsPlaylists} false it behaves like an
 * adapter build that cannot read playlists: it throws {@link Unsupported} for that read. It never reaches a platform.
 */
public final class PlaylistPlatform implements PlatformAdapter {

    public static final String PLATFORM = "listing";

    private final boolean supportsPlaylists;
    private final Map<String, Set<String>> membership = new HashMap<>();

    public PlaylistPlatform(boolean supportsPlaylists) {
        this.supportsPlaylists = supportsPlaylists;
    }

    /** Sets which platform items the playlist contains. */
    public PlaylistPlatform playlist(String playlistId, String... itemIds) {
        membership.put(playlistId, Set.of(itemIds));
        return this;
    }

    @Override
    public Set<String> playlistItemIds(String accessToken, String playlistId) {
        if (!supportsPlaylists) {
            throw new Unsupported("reading playlist membership");
        }
        return membership.getOrDefault(playlistId, Set.of());
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public String displayName() {
        return "Playlist test";
    }

    @Override
    public String redirectUri() {
        return "https://api.example.test/channel-connections/callback";
    }

    @Override
    public PlatformCapabilities capabilities() {
        return new PlatformCapabilities(false, false, false, false, false);
    }

    @Override
    public List<PlatformFeature> features() {
        return List.of(new PlatformFeature(ACCOUNT_FEATURE, Set.of("listing.account")));
    }

    @Override
    public URI authorizationUri(ConsentRequest request) {
        throw new Unsupported("consent");
    }

    @Override
    public TokenSet exchangeCode(String code, String codeVerifier, String redirectUri) {
        throw new Unsupported("consent");
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        throw new Unsupported("refresh");
    }

    @Override
    public void revoke(String token) {
        throw new Unsupported("revocation");
    }

    @Override
    public Optional<AccountIdentity> identify(String accessToken) {
        return Optional.empty();
    }

    @Override
    public PublishResult publish(String accessToken, PublishRequest request) {
        throw new Unsupported("publishing");
    }

    @Override
    public PublicationStatus status(String accessToken, String platformItemId) {
        throw new Unsupported("status");
    }

    @Override
    public void delete(String accessToken, String platformItemId) {
        throw new Unsupported("deleting");
    }

    @Override
    public List<PublishedItem> listPublished(String accessToken, int limit) {
        throw new Unsupported("listing published items");
    }

    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                     Set<String> metrics) {
        return Map.of();
    }
}
