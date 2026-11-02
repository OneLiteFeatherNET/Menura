package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Verifies a JWT before any of its claims is used (RFC 8725). The order is fixed: the algorithm in the header must
 * be on the pinned list (so {@code none} and HMAC with a public key never pass), the key id must name a key of the
 * set, the signature must verify, and only then are issuer, audience and expiry compared against the injected clock.
 * Every refusal is a 401 {@link ProblemException}; the detail never says which check failed.
 */
public final class TokenVerifier {

    static final String REFUSED_TITLE = "Unauthorized";
    static final String REFUSED_DETAIL = "The access token is missing, invalid or expired.";

    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Set<JWSAlgorithm> acceptedAlgorithms;
    private final Supplier<JWKSet> keys;

    public TokenVerifier(Clock clock, String issuer, String audience, Set<JWSAlgorithm> acceptedAlgorithms,
                         Supplier<JWKSet> keys) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.acceptedAlgorithms = Set.copyOf(Objects.requireNonNull(acceptedAlgorithms, "acceptedAlgorithms"));
        this.keys = Objects.requireNonNull(keys, "keys");
        if (this.acceptedAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("at least one signature algorithm must be accepted");
        }
    }

    /** Returns the claims of a valid token, or throws a 401 {@link ProblemException}. */
    public JWTClaimsSet verify(String token) {
        SignedJWT jwt = parseSigned(token);
        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        if (algorithm == null || !acceptedAlgorithms.contains(algorithm)) {
            throw refused();
        }
        JWK key = keyFor(jwt);
        if (!signatureValid(jwt, algorithm, key)) {
            throw refused();
        }
        JWTClaimsSet claims = claimsOf(jwt);
        checkTimeAndIdentity(claims);
        return claims;
    }

    private SignedJWT parseSigned(String token) {
        if (token == null || token.isBlank()) {
            throw refused();
        }
        try {
            JWT parsed = JWTParser.parse(token);
            if (parsed instanceof SignedJWT signed) {
                return signed;
            }
            throw refused();
        } catch (ParseException e) {
            throw refused();
        }
    }

    private JWK keyFor(SignedJWT jwt) {
        String keyId = jwt.getHeader().getKeyID();
        if (keyId == null) {
            throw refused();
        }
        JWK key = keys.get().getKeyByKeyId(keyId);
        if (key == null) {
            throw refused();
        }
        return key;
    }

    private static boolean signatureValid(SignedJWT jwt, JWSAlgorithm algorithm, JWK key) {
        try {
            JWSVerifier verifier;
            if (key instanceof RSAKey rsa && algorithm.getName().startsWith("RS")) {
                verifier = new RSASSAVerifier(rsa.toRSAPublicKey());
            } else if (key instanceof ECKey ec && algorithm.getName().startsWith("ES")) {
                verifier = new ECDSAVerifier(ec.toECPublicKey());
            } else {
                return false;
            }
            return jwt.verify(verifier);
        } catch (Exception e) {
            return false;
        }
    }

    private static JWTClaimsSet claimsOf(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw refused();
        }
    }

    private void checkTimeAndIdentity(JWTClaimsSet claims) {
        Instant now = clock.instant();
        if (claims.getExpirationTime() == null) {
            throw refused();
        }
        if (!now.isBefore(claims.getExpirationTime().toInstant())) {
            throw refused();
        }
        if (claims.getNotBeforeTime() != null && now.isBefore(claims.getNotBeforeTime().toInstant())) {
            throw refused();
        }
        if (!issuer.equals(claims.getIssuer())) {
            throw refused();
        }
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(audience)) {
            throw refused();
        }
    }

    private static ProblemException refused() {
        return new ProblemException(401, REFUSED_TITLE, REFUSED_DETAIL);
    }
}
