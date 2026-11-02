package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenVerifierTest {

    private static final String ISSUER = "https://api-menura.onelitefeather.net";
    private static final String AUDIENCE = "https://api-menura.onelitefeather.net";
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RSAKey key = TestKeys.newRsaKey(TestKeys.KEY_ID);

    private TokenVerifier verifier() {
        return new TokenVerifier(CLOCK, ISSUER, AUDIENCE, Set.of(JWSAlgorithm.RS256),
                () -> new JWKSet(key.toPublicJWK()));
    }

    private JWTClaimsSet.Builder validClaims() {
        return TestKeys.claims(ISSUER, AUDIENCE, NOW.minusSeconds(60), NOW.plusSeconds(600));
    }

    @Test
    void acceptsTokenSignedWithPinnedKeyForExpectedIssuerAndAudience() {
        JWTClaimsSet claims = verifier().verify(TestKeys.sign(key, validClaims().build()));

        assertEquals("00000000-0000-4000-8000-000000000001", claims.getSubject());
    }

    @Test
    void refusesTokenWithWrongAudienceWith401() {
        String token = TestKeys.sign(key, TestKeys.claims(ISSUER, "https://other.example",
                NOW.minusSeconds(60), NOW.plusSeconds(600)).build());

        assertUnauthorized(token, "a token for another audience must be refused");
    }

    @Test
    void refusesExpiredTokenAgainstInjectedClock() {
        String token = TestKeys.sign(key, TestKeys.claims(ISSUER, AUDIENCE, NOW.minusSeconds(900),
                NOW.minusSeconds(1)).build());

        assertUnauthorized(token, "a token past its expiry must be refused at the injected time");
    }

    @Test
    void refusesTokenWithoutExpiry() {
        JWTClaimsSet noExpiry = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE)
                .subject("someone").build();

        assertUnauthorized(TestKeys.sign(key, noExpiry), "a token without expiry must be refused");
    }

    @Test
    void refusesUnsignedTokenWithAlgorithmNone() {
        String token = new PlainJWT(validClaims().build()).serialize();

        assertUnauthorized(token, "an unsigned token (alg none) must be refused");
    }

    @Test
    void refusesHmacTokenKeyedWithThePublicKeyMaterial() throws JOSEException {
        byte[] publicKeyBytes = key.toPublicKey().getEncoded();
        SignedJWT forged = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(TestKeys.KEY_ID).build(), validClaims().build());
        forged.sign(new MACSigner(publicKeyBytes));

        assertUnauthorized(forged.serialize(), "algorithm confusion with HS256 must be refused");
    }

    @Test
    void refusesTokenSignedByAnotherKey() {
        RSAKey other = TestKeys.newRsaKey(TestKeys.KEY_ID);

        assertUnauthorized(TestKeys.sign(other, validClaims().build()),
                "a signature from a key that is not pinned must be refused");
    }

    @Test
    void refusesTokenWithUnknownKeyId() {
        RSAKey unknown = TestKeys.newRsaKey("rotated-away");

        assertUnauthorized(TestKeys.sign(unknown, validClaims().build()),
                "a token whose key id is not in the key set must be refused");
    }

    @Test
    void refusesTokenFromAnotherIssuer() {
        String token = TestKeys.sign(key, TestKeys.claims("https://login.example", AUDIENCE,
                NOW.minusSeconds(60), NOW.plusSeconds(600)).build());

        assertUnauthorized(token, "a token from another issuer must be refused");
    }

    private void assertUnauthorized(String token, String message) {
        ProblemException refused = assertThrows(ProblemException.class, () -> verifier().verify(token), message);
        assertEquals(401, refused.status(), message);
    }
}
