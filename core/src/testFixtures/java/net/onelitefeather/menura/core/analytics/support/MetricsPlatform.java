package net.onelitefeather.menura.core.analytics.support;

import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformFeature;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A platform whose listing and metrics the test script: the items it lists, the metrics it reports per item, or a failure it
 * throws instead. It never reaches a platform.
 */
public final class MetricsPlatform implements PlatformAdapter {

    public static final String PLATFORM = "metrics";

    private final boolean metricsSupported;
    private final Map<String, Map<String, Long>> metricsByItem = new HashMap<>();
    private final Map<String, RuntimeException> failureByItem = new HashMap<>();
    private final List<String> reads = new ArrayList<>();
    private final List<List<String>> batches = new ArrayList<>();
    private int batchSize = 50;
    private List<PublishedItem> items = List.of();
    private RuntimeException failure;
    private final List<Integer> limits = new ArrayList<>();

    /** A platform that reports metrics when {@code metricsSupported} is true, and says so in its capabilities. */
    public MetricsPlatform(boolean metricsSupported) {
        this.metricsSupported = metricsSupported;
    }

    /** The values the platform reports for one item, as the metrics operation returns them. */
    public void report(String platformItemId, Map<String, Long> values) {
        metricsByItem.put(platformItemId, Map.copyOf(values));
    }

    /** Makes the metrics operation of one item fail with this exception. */
    public void failMetricsOf(String platformItemId, RuntimeException exception) {
        failureByItem.put(platformItemId, exception);
    }

    /** The items whose metrics were read, in order. */
    public List<String> reads() {
        return List.copyOf(reads);
    }

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
        return "Metrics";
    }

    @Override
    public String redirectUri() {
        return "https://api.example.test/channel-connections/callback";
    }

    @Override
    public PlatformCapabilities capabilities() {
        return new PlatformCapabilities(false, false, true, metricsSupported, false);
    }

    @Override
    public List<PlatformFeature> features() {
        return List.of(new PlatformFeature(ACCOUNT_FEATURE, Set.of("metrics.account")));
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
    public int metricsBatchSize() {
        return batchSize;
    }

    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                     Set<String> metrics) {
        batches.add(List.copyOf(platformItemIds));
        reads.addAll(platformItemIds);
        if (!metricsSupported) {
            throw new Unsupported("reading metrics");
        }
        Map<String, Map<String, Long>> result = new java.util.LinkedHashMap<>();
        for (String platformItemId : platformItemIds) {
            RuntimeException failed = failureByItem.get(platformItemId);
            if (failed != null) {
                throw failed;
            }
            Map<String, Long> values = metricsByItem.get(platformItemId);
            if (values == null) {
                continue;
            }
            Map<String, Long> requested = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                if (metrics.contains(entry.getKey())) {
                    requested.put(entry.getKey(), entry.getValue());
                }
            }
            result.put(platformItemId, requested);
        }
        return result;
    }

    /** The batches of the metrics operation so far, in call order: the item identifiers of each call. */
    public List<List<String>> batches() {
        return List.copyOf(batches);
    }

    /** Sets the largest number of items one metrics call may request. */
    public void batchSize(int size) {
        this.batchSize = size;
    }

    /** An item as the platform lists it. */
    public static PublishedItem item(String platformItemId, String title, Instant publishedAt) {
        return new PublishedItem(platformItemId, "https://video.example.test/" + platformItemId, title, publishedAt,
                "video");
    }
}
