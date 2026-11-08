package net.onelitefeather.menura.core.channel.support;

import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformFeature;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An in-memory adapter for the contract tests: capabilities, features, listed items and metrics are set by the test.
 * It never reaches a platform.
 */
public final class FakePlatform implements PlatformAdapter {

    private final PlatformCapabilities capabilities;
    private final List<PublishedItem> items;
    private final Map<String, Long> metrics;

    public FakePlatform(PlatformCapabilities capabilities, List<PublishedItem> items, Map<String, Long> metrics) {
        this.capabilities = capabilities;
        this.items = items;
        this.metrics = metrics;
    }

    @Override
    public String platform() {
        return "fake";
    }

    @Override
    public String displayName() {
        return "Fake";
    }

    @Override
    public String redirectUri() {
        return "https://api.example.test/channel-connections/callback";
    }

    @Override
    public PlatformCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public List<PlatformFeature> features() {
        return List.of(new PlatformFeature(ACCOUNT_FEATURE, Set.of("fake.account")),
                new PlatformFeature("publish", Set.of("fake.publish")));
    }

    @Override
    public URI authorizationUri(ConsentRequest request) {
        return URI.create("https://consent.example.test/auth?state=" + request.state());
    }

    @Override
    public TokenSet exchangeCode(String code, String codeVerifier, String redirectUri) {
        throw new UnsupportedOperationException("the fake platform has no consent");
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        throw new UnsupportedOperationException("the fake platform has no refresh");
    }

    @Override
    public void revoke(String token) {
        throw new UnsupportedOperationException("the fake platform has no revocation");
    }

    @Override
    public Optional<AccountIdentity> identify(String accessToken) {
        return Optional.empty();
    }

    @Override
    public PublishResult publish(String accessToken, PublishRequest request) {
        throw new Unsupported("publish");
    }

    @Override
    public PublicationStatus status(String accessToken, String platformItemId) {
        throw new Unsupported("status");
    }

    @Override
    public void delete(String accessToken, String platformItemId) {
        throw new Unsupported("delete");
    }

    @Override
    public List<PublishedItem> listPublished(String accessToken, int limit) {
        return items;
    }

    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                     Set<String> requested) {
        Map<String, Map<String, Long>> result = new java.util.LinkedHashMap<>();
        for (String platformItemId : platformItemIds) {
            result.put(platformItemId, metrics);
        }
        return result;
    }
}
