package net.onelitefeather.menura.server.web;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.server.problem.ProblemResponse;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Enforces the per-person budget before the request reaches its handler. An exceeded budget answers 429 with
 * Retry-After and a Problem Details body; the handler does not run.
 */
@Filter("/**")
public class PersonRateLimitFilter implements HttpServerFilter {

    private final PersonKeyResolver personKeyResolver;
    private final PersonRateLimiter limiter;

    public PersonRateLimitFilter(PersonKeyResolver personKeyResolver, PersonRateLimiter limiter) {
        this.personKeyResolver = personKeyResolver;
        this.limiter = limiter;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<String> person = personKeyResolver.resolve(request);
        if (person.isEmpty()) {
            return chain.proceed(request);
        }
        PersonRateLimiter.Decision decision = limiter.tryAcquire(person.get());
        if (decision.allowed()) {
            return chain.proceed(request);
        }
        ProblemDetails details = ProblemDetails.of(429, "Too Many Requests",
                "The request budget of this person is used up. Retry after the number of seconds in Retry-After.",
                request.getPath());
        MutableHttpResponse<?> refused = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(decision.retryAfterSeconds()))
                .contentType(MediaType.of(ProblemDetails.MEDIA_TYPE))
                .body(ProblemResponse.from(details));
        return Publishers.just(refused);
    }
}
