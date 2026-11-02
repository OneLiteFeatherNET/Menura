package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.UUID;

/** Builds the authorization server's own components from {@link AuthSettings}. */
@Factory
public class AuthFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFactory.class);

    @Singleton
    MenuraTokenIssuer menuraTokenIssuer(AuthSettings settings, Clock clock) {
        return new MenuraTokenIssuer(clock, settings.issuer(), settings.audience(), settings.tokenLifetime(),
                signingKey(settings.signingKeyJwk()));
    }

    @Singleton
    RoleMapping roleMapping(AuthSettings settings) {
        return RoleMapping.fromConfiguration(settings.roleGroups());
    }

    /**
     * The configured key, or an ephemeral one when none is configured. An ephemeral key makes every issued token and
     * session unverifiable after a restart, which is acceptable locally and must not happen in production.
     */
    static RSAKey signingKey(String jwk) {
        try {
            if (jwk == null || jwk.isBlank()) {
                LOG.warn("No signing key configured (MENURA_AUTH_SIGNING_KEY): using an ephemeral key, "
                        + "sessions end on restart.");
                return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            }
            RSAKey key = RSAKey.parse(jwk);
            if (!key.isPrivate()) {
                throw new IllegalStateException("MENURA_AUTH_SIGNING_KEY must hold the private key");
            }
            return key;
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("MENURA_AUTH_SIGNING_KEY is not a JWK", e);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("the ephemeral signing key could not be generated", e);
        }
    }
}
