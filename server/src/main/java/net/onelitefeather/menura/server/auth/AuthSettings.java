package net.onelitefeather.menura.server.auth;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.auth.Role;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Configuration of the authorization server and of the upstream Entra ID application. Every value comes from the
 * {@code menura.auth} section of application.yml, which reads an environment variable with a local-development
 * default. An empty Entra ID setting disables sign-in (the sign-in endpoints answer 503) without stopping the server.
 *
 * <p>Entra ID app registration: a web application with the redirect URI {@code MENURA_ENTRA_REDIRECT_URI}, the
 * {@code openid} and {@code profile} permissions, and the optional {@code groups} claim in the ID token. The group
 * object ids of each Menura role are listed in {@code MENURA_AUTH_ROLE_<ROLE>_GROUPS}, comma-separated.
 */
@Singleton
public class AuthSettings {

    @Value("${menura.auth.issuer}")
    String issuer;

    @Value("${menura.auth.audience}")
    String audience;

    @Value("${menura.auth.web-app-url}")
    String webAppUrl;

    @Value("${menura.auth.token-lifetime}")
    String tokenLifetime;

    @Value("${menura.auth.session-lifetime}")
    String sessionLifetime;

    @Value("${menura.auth.signing-key}")
    String signingKeyJwk;

    @Value("${menura.auth.entra.issuer}")
    String entraIssuer;

    @Value("${menura.auth.entra.client-id}")
    String entraClientId;

    @Value("${menura.auth.entra.client-secret}")
    String entraClientSecret;

    @Value("${menura.auth.entra.redirect-uri}")
    String entraRedirectUri;

    @Value("${menura.auth.role-groups.viewer}")
    String viewerGroups;

    @Value("${menura.auth.role-groups.editor}")
    String editorGroups;

    @Value("${menura.auth.role-groups.publisher}")
    String publisherGroups;

    @Value("${menura.auth.role-groups.operator}")
    String operatorGroups;

    @Value("${menura.auth.role-groups.admin}")
    String adminGroups;

    /** The issuer of Menura's tokens, which is the API host. */
    public String issuer() {
        return issuer;
    }

    /** The audience of Menura's access tokens. */
    public String audience() {
        return audience;
    }

    /** Where the browser lands after a successful sign-in. */
    public String webAppUrl() {
        return webAppUrl;
    }

    /** How long an access token, and therefore a web session, lasts. */
    public Duration tokenLifetime() {
        return Duration.parse(tokenLifetime);
    }

    /** How long a web session lasts after its sign-in, counted from the sign-in and not refreshed by use. */
    public Duration sessionLifetime() {
        return Duration.parse(sessionLifetime);
    }

    /** Menura's private RSA signing key as a JWK, or empty to use an ephemeral key (local development only). */
    public String signingKeyJwk() {
        return signingKeyJwk;
    }

    public String entraIssuer() {
        return entraIssuer;
    }

    public String entraClientId() {
        return entraClientId;
    }

    public String entraClientSecret() {
        return entraClientSecret;
    }

    /** The callback URL registered for the Entra ID application. */
    public String entraRedirectUri() {
        return entraRedirectUri;
    }

    /** Per role, the comma-separated Entra group object ids that grant it. */
    public Map<Role, String> roleGroups() {
        Map<Role, String> groups = new EnumMap<>(Role.class);
        groups.put(Role.VIEWER, viewerGroups);
        groups.put(Role.EDITOR, editorGroups);
        groups.put(Role.PUBLISHER, publisherGroups);
        groups.put(Role.OPERATOR, operatorGroups);
        groups.put(Role.ADMIN, adminGroups);
        return groups;
    }
}
