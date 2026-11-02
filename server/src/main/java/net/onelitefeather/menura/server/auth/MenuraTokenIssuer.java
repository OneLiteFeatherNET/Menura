package net.onelitefeather.menura.server.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.onelitefeather.menura.core.auth.Role;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Menura's own authorization server issues the access tokens it accepts. A token is signed with RS256 by Menura's
 * key, names Menura as issuer and audience, and carries the roles that the upstream identity provider's groups
 * mapped to. Entra ID tokens are never passed on.
 */
public final class MenuraTokenIssuer {

    static final String ROLES_CLAIM = "roles";

    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;
    private final RSAKey signingKey;

    /** A token and the instant it stops being accepted. */
    public record IssuedToken(String token, Instant expiresAt) {
    }

    public MenuraTokenIssuer(Clock clock, String issuer, String audience, Duration lifetime, RSAKey signingKey) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey");
        if (!signingKey.isPrivate()) {
            throw new IllegalArgumentException("the signing key must hold its private part");
        }
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("the token lifetime must be positive");
        }
    }

    /** Issues a token for a person with exactly the given roles. */
    public IssuedToken issue(String subject, Set<Role> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(lifetime);
        List<String> roleNames = roles.stream().map(Role::name).sorted().collect(Collectors.toList());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim(ROLES_CLAIM, roleNames)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(signingKey));
            return new IssuedToken(jwt.serialize(), expiresAt);
        } catch (JOSEException e) {
            throw new IllegalStateException("signing an access token failed", e);
        }
    }

    /** The public half of the signing key, for the verifier and for any published key set. */
    public JWKSet publicKeys() {
        return new JWKSet(signingKey.toPublicJWK());
    }

    /** The roles named in the claims of a verified token. Unknown names grant nothing. */
    static Set<Role> rolesOf(JWTClaimsSet claims) {
        try {
            List<String> names = claims.getStringListClaim(ROLES_CLAIM);
            if (names == null) {
                return Set.of();
            }
            return names.stream().map(Role::parse).flatMap(Optional::stream).collect(Collectors.toUnmodifiableSet());
        } catch (ParseException e) {
            return Set.of();
        }
    }

    /** A verifier that accepts exactly the tokens this issuer signs, for this issuer and audience. */
    public TokenVerifier verifier() {
        return new TokenVerifier(clock, issuer, audience, Set.of(JWSAlgorithm.RS256), this::publicKeys);
    }
}
