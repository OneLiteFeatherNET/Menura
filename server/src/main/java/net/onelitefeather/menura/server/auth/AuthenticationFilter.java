package net.onelitefeather.menura.server.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.web.SessionCookie;
import org.reactivestreams.Publisher;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter order of the access rules, which is documented here and tested in AccessOrderTest:
 * <ol>
 *   <li>this filter (authentication): sets {@link CurrentPerson} from the web session cookie or a verified bearer
 *       token; it never refuses a request itself;</li>
 *   <li>{@code AccessFilter} (default deny, anonymous allow-list): answers 401 for a request without a person;</li>
 *   <li>{@code RoleCheckFilter}: answers 403 for a person without the declared roles;</li>
 *   <li>{@code PersonRateLimitFilter}: the person budget, keyed by the person id.</li>
 * </ol>
 * Rate limits and role logic therefore never see an anonymous request on a protected route.
 *
 * <p>A session is found by the digest of its cookie secret. An unknown or expired session, and an invalid token, are
 * treated as anonymous.
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
                .flatMap(secret -> store.findSession(SecretHasher.hash(secret), clock.instant()))
                .map(session -> new CurrentPerson(session.personId(), session.subject(), session.roles(),
                        Channel.WEB));
    }

    private Optional<CurrentPerson> verified(String token) {
        try {
            JWTClaimsSet claims = issuer.verifier().verify(token);
            Optional<UUID> person = MenuraTokenIssuer.personOf(claims);
            Optional<Channel> channel = MenuraTokenIssuer.channelOf(claims);
            if (person.isEmpty() || channel.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CurrentPerson(person.get(), claims.getSubject(),
                    MenuraTokenIssuer.rolesOf(claims), channel.get()));
        } catch (ProblemException refused) {
            return Optional.empty();
        }
    }
}
