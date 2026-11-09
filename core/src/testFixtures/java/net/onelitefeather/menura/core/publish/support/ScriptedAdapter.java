package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformFeature;
import net.onelitefeather.menura.core.channel.StepwisePublisher;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A platform adapter that answers publication calls from a script. Each publish call takes the next scripted outcome: a
 * {@link PublishResult}, or a {@link RuntimeException} to throw. With the script empty, a publish succeeds with the next
 * item identifier. The adapter records every publish request and the access token it was called with.
 */
public final class ScriptedAdapter implements PlatformAdapter {

    private final boolean nativeScheduling;
    private StepwisePublisher stepwise;
    private final Deque<Object> publishOutcomes = new ArrayDeque<>();
    private final Deque<PublicationStatus> statusOutcomes = new ArrayDeque<>();
    private final List<PublishRequest> requests = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private Runnable beforePublish = () -> {
    };
    private int counter;

    public ScriptedAdapter(boolean nativeScheduling) {
        this.nativeScheduling = nativeScheduling;
    }

    /** Makes this adapter publish in steps through the given publisher (the YouTube way). */
    public ScriptedAdapter stepwise(StepwisePublisher publisher) {
        this.stepwise = publisher;
        return this;
    }

    @Override
    public Optional<StepwisePublisher> stepwise() {
        return Optional.ofNullable(stepwise);
    }

    /** The next publish call throws or returns this outcome. */
    public ScriptedAdapter nextPublish(Object outcome) {
        publishOutcomes.add(outcome);
        return this;
    }

    /** The next status call answers this. With the script empty a status call answers pending. */
    public ScriptedAdapter nextStatus(PublicationStatus status) {
        statusOutcomes.add(status);
        return this;
    }

    /** Runs before each publish call, to observe the state the platform sees at that moment. */
    public ScriptedAdapter beforePublish(Runnable hook) {
        this.beforePublish = hook;
        return this;
    }

    public List<PublishRequest> requests() {
        return List.copyOf(requests);
    }

    public List<String> tokens() {
        return List.copyOf(tokens);
    }

    @Override
    public String platform() {
        return "youtube";
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public String redirectUri() {
        return "https://api.example.test/channel-connections/callback";
    }

    @Override
    public PlatformCapabilities capabilities() {
        return new PlatformCapabilities(true, nativeScheduling, true, true, true);
    }

    @Override
    public List<PlatformFeature> features() {
        return List.of(new PlatformFeature(ACCOUNT_FEATURE, Set.of()));
    }

    @Override
    public URI authorizationUri(ConsentRequest request) {
        return URI.create("https://consent.example.test/authorize");
    }

    @Override
    public TokenSet exchangeCode(String code, String codeVerifier, String redirectUri) {
        throw new Unsupported("exchanging a code");
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        throw new Unsupported("refreshing a grant");
    }

    @Override
    public void revoke(String token) {
    }

    @Override
    public Optional<AccountIdentity> identify(String accessToken) {
        return Optional.empty();
    }

    @Override
    public PublishResult publish(String accessToken, PublishRequest request) {
        tokens.add(accessToken);
        requests.add(request);
        beforePublish.run();
        Object outcome = publishOutcomes.poll();
        if (outcome instanceof RuntimeException failure) {
            throw failure;
        }
        if (outcome instanceof PublishResult result) {
            return result;
        }
        counter++;
        return new PublishResult("item-" + counter, "https://video.example.test/item-" + counter);
    }

    @Override
    public PublicationStatus status(String accessToken, String platformItemId) {
        PublicationStatus next = statusOutcomes.poll();
        return next == null ? PublicationStatus.PENDING : next;
    }

    @Override
    public void delete(String accessToken, String platformItemId) {
    }

    @Override
    public List<PublishedItem> listPublished(String accessToken, int limit) {
        return List.of();
    }

    @Override
    public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds, Set<String> metrics) {
        return Map.of();
    }

    /** A rate-limit or refusal from the platform, as the adapter reports it. */
    public static PlatformException rateLimited() {
        return new PlatformException(PlatformException.Kind.RATE_LIMITED, "rate limited", null);
    }

    public static PlatformException unavailable() {
        return new PlatformException(PlatformException.Kind.UNAVAILABLE, "unreachable", null);
    }

    public static PlatformException refused() {
        return new PlatformException(PlatformException.Kind.REJECTED, "refused", null);
    }
}
