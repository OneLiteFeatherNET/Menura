package net.onelitefeather.menura.server.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.reactivestreams.Publisher;

import java.time.Clock;
import java.util.Optional;

/**
 * Establishes the verified person of a request, from either a Menura access token in the Authorization header or the
 * web session cookie. It never refuses a request itself: a request with no verified person is anonymous, and the role
 * check decides what that means for the endpoint. An invalid token or an unknown session is treated as anonymous.
 */
@Filter(Filter.MATCH_ALL_PATTERN)
public class AuthenticationFilter implements HttpServerFilter {

    private static final String BEARER = "Bearer ";

    private final MenuraTokenIssuer issuer;
    private final AuthStore store;
    private final Clock clock;

    public AuthenticationFilter(MenuraTokenIssuer issuer, AuthStore store, Clock clock) {
        this.issuer = issuer;
        this.store = store;
        this.clock = clock;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        personOf(request).ifPresent(person -> request.setAttribute(CurrentPerson.ATTRIBUTE, person));
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }

    private Optional<CurrentPerson> personOf(HttpRequest<?> request) {
        Optional<CurrentPerson> fromToken = request.getHeaders().getAuthorization()
                .filter(value -> value.regionMatches(true, 0, BEARER, 0, BEARER.length()))
                .map(value -> value.substring(BEARER.length()).trim())
                .flatMap(this::verified);
        if (fromToken.isPresent()) {
            return fromToken;
        }
        return Optional.ofNullable(request.getCookies().get(SessionCookie.NAME))
                .map(cookie -> cookie.getValue())
                .flatMap(secret -> store.findSession(Secrets.hash(secret), clock.instant()))
                .flatMap(session -> verified(session.accessToken()));
    }

    private Optional<CurrentPerson> verified(String token) {
        try {
            JWTClaimsSet claims = issuer.verifier().verify(token);
            return Optional.of(new CurrentPerson(claims.getSubject(), MenuraTokenIssuer.rolesOf(claims)));
        } catch (ProblemException refused) {
            return Optional.empty();
        }
    }
}
