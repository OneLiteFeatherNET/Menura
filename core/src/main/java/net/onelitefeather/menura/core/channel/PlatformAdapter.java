package net.onelitefeather.menura.core.channel;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The port every platform channel type implements (principle 13): one adapter per platform, talking only to that
 * platform's official API. Common code never branches on the platform name; it reads {@link #capabilities()} and
 * {@link #features()} instead.
 *
 * <p>Connection operations (consent, token exchange, refresh, revocation, identity) are implemented by the connection
 * change. Publishing, status, deletion, listing and metrics are part of the contract so that every platform change
 * implements the same set; an adapter whose build does not implement one throws {@link Unsupported}.
 *
 * <p>Tokens are passed in as arguments and never stored by an adapter. Errors from the platform are
 * {@link PlatformException}s whose messages carry no token and no response body.
 */
public interface PlatformAdapter {

    /** Name of the base feature that every connection needs to identify the account. Its scopes are always requested. */
    String ACCOUNT_FEATURE = "account";

    /** The platform identifier, lower case, for example {@code youtube}. Stored with every channel. */
    String platform();

    /** The name shown to people, for example {@code YouTube}. */
    String displayName();

    /** The redirect URI registered at the platform for this app. */
    String redirectUri();

    /** What the platform offers and what this adapter build supports. */
    PlatformCapabilities capabilities();

    /** The features a channel can be enabled for, each with the scopes it needs. Includes {@link #ACCOUNT_FEATURE}. */
    List<PlatformFeature> features();

    /** Builds the platform's own consent URL for one authorization code flow with PKCE. */
    URI authorizationUri(ConsentRequest request);

    /** Exchanges an authorization code for tokens at the platform's token endpoint. */
    TokenSet exchangeCode(String code, String codeVerifier, String redirectUri);

    /** Obtains a new access token from a refresh token; the response may carry a rotated refresh token. */
    TokenSet refresh(String refreshToken);

    /** Revokes a grant at the platform. Called only when {@link PlatformCapabilities#revocation()} is true. */
    void revoke(String token);

    /** The platform account the access token belongs to, or empty when the account has no channel of this platform. */
    Optional<AccountIdentity> identify(String accessToken);

    PublishResult publish(String accessToken, PublishRequest request);

    PublicationStatus status(String accessToken, String platformItemId);

    void delete(String accessToken, String platformItemId);

    /** The channel's own published items as the platform lists them, at most {@code limit}. */
    List<PublishedItem> listPublished(String accessToken, int limit);

    /**
     * The platform item identifiers that a playlist of the channel contains. Read-only: phase 1 never writes playlists.
     * An adapter whose build cannot read playlist membership throws {@link Unsupported}; callers show the playlist as
     * not checked.
     */
    default Set<String> playlistItemIds(String accessToken, String playlistId) {
        throw new Unsupported("reading playlist membership");
    }

    /**
     * Reads the requested metrics of one item. A metric the platform does not expose is left out of the map; callers
     * record it as unavailable, never as zero.
     */
    Map<String, Long> readMetrics(String accessToken, String platformItemId, Set<String> metrics);

    /**
     * The scopes to request for the given feature names, always including the base account feature. A feature name
     * the adapter does not declare contributes nothing.
     */
    default Set<String> scopesFor(Set<String> featureNames) {
        Set<String> scopes = new HashSet<>();
        for (PlatformFeature feature : features()) {
            if (ACCOUNT_FEATURE.equals(feature.name()) || featureNames.contains(feature.name())) {
                scopes.addAll(feature.scopes());
            }
        }
        return Set.copyOf(scopes);
    }

    /** Parameters of one consent redirect. The state and challenge are created by the connection service. */
    record ConsentRequest(String state, String codeChallenge, String redirectUri, Set<String> scopes) {
    }

    /** Tokens returned by a platform. {@code refreshToken} is null when the platform issued none. */
    record TokenSet(String accessToken, String refreshToken, Instant accessExpiresAt, Set<String> scopes) {

        @Override
        public String toString() {
            return "TokenSet[tokens=redacted]";
        }
    }

    /** The platform account of a grant: its stable identifier and the name shown to people. */
    record AccountIdentity(String platformAccountId, String displayName) {
    }

    enum Visibility { PRIVATE, UNLISTED, PUBLIC }

    /** A publication request. Platform-specific options are added by the platform changes. */
    record PublishRequest(String title, Visibility visibility, Instant publishAt) {
    }

    record PublishResult(String platformItemId, String url) {
    }

    enum PublicationStatus { PENDING, PROCESSING, PUBLISHED, FAILED, REMOVED }

    /**
     * One item the platform lists for a channel. The description is the text the platform shows under the item; it is
     * empty when the platform does not report one. Series assignment reads the hashtags in it.
     */
    record PublishedItem(String platformItemId, String url, String title, Instant publishedAt, String format,
                         String description) {

        /** An item without a description, as adapters that do not report one list it. */
        public PublishedItem(String platformItemId, String url, String title, Instant publishedAt, String format) {
            this(platformItemId, url, title, publishedAt, format, "");
        }
    }

    /** Thrown by operations an adapter build does not yet implement. The server answers 501. */
    final class Unsupported extends RuntimeException {
        public Unsupported(String operation) {
            super(operation + " is not available for this platform in this build");
        }
    }
}
