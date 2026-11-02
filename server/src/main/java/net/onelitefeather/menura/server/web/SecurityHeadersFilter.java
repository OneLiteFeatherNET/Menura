package net.onelitefeather.menura.server.web;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

/**
 * Adds the transport and content headers to every response, errors included (principle 11, RFC 6797 and
 * RFC 9110): HSTS, no referrer, and no MIME sniffing. The gateway adds the same headers on the web route, which
 * serves static files without this filter.
 */
@Filter("/**")
public class SecurityHeadersFilter implements HttpServerFilter {

    static final String STRICT_TRANSPORT_SECURITY = "max-age=31536000";

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.map(chain.proceed(request), response -> {
            response.header("Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
            response.header("Referrer-Policy", "no-referrer");
            response.header("X-Content-Type-Options", "nosniff");
            return response;
        });
    }
}
