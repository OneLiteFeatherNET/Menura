package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.channel.HttpTransport;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformCapabilities;
import net.onelitefeather.menura.core.channel.PlatformFeature;
import net.onelitefeather.menura.core.channel.StepwisePublisher;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Chooses the YouTube adapter of a {@link YouTubeMode}. {@link YouTubeMode#LIVE} is the {@link YouTubeAdapter} over the live
 * transport. {@link YouTubeMode#SIMULATED} is the same adapter over {@link SimulatedYouTube}, so the real request and response
 * handling runs against generated data; the consent page is skipped, because the simulated callback receives a code directly.
 */
public final class YouTubeAdapters {

    /** Fixed, so that the simulated channel and its back catalogue are the same on every start. */
    static final long SEED = 20261108L;

    static final String SIMULATED_CODE = "simulated-code";

    private static final Logger LOG = Logger.getLogger(YouTubeAdapters.class.getName());

    private YouTubeAdapters() {
    }

    public static PlatformAdapter create(Clock clock, YouTubeAdapter.Settings settings, YouTubeMode mode,
                                         HttpTransport liveTransport) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(liveTransport, "liveTransport");
        return switch (mode) {
            case LIVE -> new YouTubeAdapter(clock, settings, liveTransport);
            case SIMULATED -> simulated(clock, settings);
        };
    }

    private static PlatformAdapter simulated(Clock clock, YouTubeAdapter.Settings settings) {
        LOG.warning("YouTube runs simulated (" + YouTubeMode.VARIABLE + "=simulated): data is generated locally and no "
                + "request reaches YouTube.");
        // A simulated run needs no app registration: blank client settings get placeholders, so a developer can connect
        // a channel without the Google credentials.
        YouTubeAdapter.Settings effective = new YouTubeAdapter.Settings(
                settings.clientId().isBlank() ? "simulated-client-id" : settings.clientId(),
                settings.clientSecret().isBlank() ? "simulated-client-secret" : settings.clientSecret(),
                settings.redirectUri());
        return new SimulatedAdapter(new YouTubeAdapter(clock, effective, new SimulatedYouTube(clock, SEED)));
    }

    /**
     * Delegates every operation to the YouTube adapter, and only replaces the consent URI: it points at the redirect URI with
     * the state and a code, so the callback of the connection completes without the consent page.
     */
    private static final class SimulatedAdapter implements PlatformAdapter {

        private final PlatformAdapter delegate;

        SimulatedAdapter(PlatformAdapter delegate) {
            this.delegate = delegate;
        }

        @Override
        public URI authorizationUri(ConsentRequest request) {
            String redirect = request.redirectUri();
            String separator = redirect.contains("?") ? "&" : "?";
            return URI.create(redirect + separator + "code=" + encode(SIMULATED_CODE) + "&state="
                    + encode(request.state()));
        }

        @Override
        public String platform() {
            return delegate.platform();
        }

        @Override
        public String displayName() {
            return delegate.displayName();
        }

        @Override
        public String redirectUri() {
            return delegate.redirectUri();
        }

        @Override
        public PlatformCapabilities capabilities() {
            return delegate.capabilities();
        }

        @Override
        public List<PlatformFeature> features() {
            return delegate.features();
        }

        @Override
        public TokenSet exchangeCode(String code, String codeVerifier, String redirectUri) {
            return delegate.exchangeCode(code, codeVerifier, redirectUri);
        }

        @Override
        public TokenSet refresh(String refreshToken) {
            return delegate.refresh(refreshToken);
        }

        @Override
        public void revoke(String token) {
            delegate.revoke(token);
        }

        @Override
        public Optional<AccountIdentity> identify(String accessToken) {
            return delegate.identify(accessToken);
        }

        @Override
        public PublishResult publish(String accessToken, PublishRequest request) {
            return delegate.publish(accessToken, request);
        }

        @Override
        public Optional<StepwisePublisher> stepwise() {
            return delegate.stepwise();
        }

        @Override
        public PublicationStatus status(String accessToken, String platformItemId) {
            return delegate.status(accessToken, platformItemId);
        }

        @Override
        public void delete(String accessToken, String platformItemId) {
            delegate.delete(accessToken, platformItemId);
        }

        @Override
        public List<PublishedItem> listPublished(String accessToken, int limit) {
            return delegate.listPublished(accessToken, limit);
        }

        @Override
        public Set<String> playlistItemIds(String accessToken, String playlistId) {
            return delegate.playlistItemIds(accessToken, playlistId);
        }

        @Override
        public Set<String> ownedPlaylistIds(String accessToken) {
            return delegate.ownedPlaylistIds(accessToken);
        }

        @Override
        public int metricsBatchSize() {
            return delegate.metricsBatchSize();
        }

        @Override
        public Map<String, Map<String, Long>> readMetrics(String accessToken, List<String> platformItemIds,
                                                         Set<String> metrics) {
            return delegate.readMetrics(accessToken, platformItemIds, metrics);
        }

        @Override
        public Set<String> scopesFor(Set<String> featureNames) {
            return delegate.scopesFor(featureNames);
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
