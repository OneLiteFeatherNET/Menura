package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.onelitefeather.menura.core.auth.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MenuraTokenIssuerTest {

    private static final String ISSUER = "https://api-menura.onelitefeather.net";
    private static final String AUDIENCE = "https://api-menura.onelitefeather.net";
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration LIFETIME = Duration.ofHours(8);

    private final RSAKey key = TestKeys.newRsaKey("menura-test");
    private final MenuraTokenIssuer issuer = new MenuraTokenIssuer(CLOCK, ISSUER, AUDIENCE, LIFETIME, key);

    @Test
    void issuedTokenVerifiesAgainstTheIssuersOwnKeysAndCarriesExactlyTheGivenRoles() throws Exception {
        MenuraTokenIssuer.IssuedToken issued = issuer.issue("person-1", Set.of(Role.VIEWER, Role.OPERATOR));

        JWTClaimsSet claims = issuer.verifier().verify(issued.token());

        assertEquals("person-1", claims.getSubject());
        assertEquals(Set.of("OPERATOR", "VIEWER"), Set.copyOf(claims.getStringListClaim(MenuraTokenIssuer.ROLES_CLAIM)));
    }

    @Test
    void tokenLivesForTheConfiguredLifetimeFromTheInjectedClock() throws Exception {
        MenuraTokenIssuer.IssuedToken issued = issuer.issue("person-1", Set.of(Role.VIEWER));

        assertEquals(NOW.plus(LIFETIME), issued.expiresAt());
        assertEquals(NOW.plus(LIFETIME), SignedJWT.parse(issued.token()).getJWTClaimsSet()
                .getExpirationTime().toInstant());
    }

    @Test
    void tokenIsSignedWithRs256AndNamesItsKey() throws Exception {
        SignedJWT parsed = SignedJWT.parse(issuer.issue("person-1", Set.of()).token());

        assertEquals(JWSAlgorithm.RS256, parsed.getHeader().getAlgorithm());
        assertEquals("menura-test", parsed.getHeader().getKeyID());
    }

    @Test
    void tokenIdIsARandomVersion4Uuid() throws Exception {
        String id = SignedJWT.parse(issuer.issue("person-1", Set.of()).token()).getJWTClaimsSet().getJWTID();

        assertEquals(4, UUID.fromString(id).version(), "identifiers are RFC 9562 version 4 UUIDs");
    }

    @Test
    void publishedKeySetHoldsOnlyThePublicPart() {
        JWKSet published = issuer.publicKeys();

        assertEquals(1, published.getKeys().size());
        assertFalse(published.getKeys().get(0).isPrivate(), "the key set must not expose the private key");
    }
}
