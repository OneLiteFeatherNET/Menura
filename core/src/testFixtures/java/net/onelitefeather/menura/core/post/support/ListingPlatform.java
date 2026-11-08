package net.onelitefeather.menura.core.post.support;

import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformFeature;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A platform whose listing the test scripts: the items it lists, or a failure it throws instead. It supports listing
 * and nothing else, and never reaches a platform.
 */
public final class ListingPlatform implements PlatformAdapter {

    public static final String PLATFORM = "listing";

    private List<PublishedItem> items = List.of();
    private RuntimeException failure;
    private final List<Integer> limits = new ArrayList<>();

    /** Replaces the items the next listings return. */
    public void list(List<PublishedItem> next) {
        this.items = List.copyOf(next);
    }

    /** Makes every listing fail with this exception, as a platform that breaks part-way would. */
    public void failListing(RuntimeException exception) {
        this.failure = exception;
    }

    /** The limits of the listings requested so far. */
    public List<Integer> limitsRequested() {
        return List.copyOf(limits);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public String displayName() {
        return "Listing";
    }

    @Override
    public String redirectUri() {
        return "https://api.example.test/channel-connections/callback";
    }

    @Override
    public PlatformCapabilities capabilities() {
        return new PlatformCapabilities(false, false, true, false, false);
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
        limits.add(limit);
        if (failure != null) {
            throw failure;
        }
        return items.size() <= limit ? items : items.subList(0, limit);
    }

    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                     Set<String> metrics) {
        return Map.of();
    }

    /** An item as the platform lists it. */
    public static PublishedItem item(String platformItemId, String title, Instant publishedAt) {
        return new PublishedItem(platformItemId, "https://video.example.test/" + platformItemId, title, publishedAt,
                "video");
    }
}
