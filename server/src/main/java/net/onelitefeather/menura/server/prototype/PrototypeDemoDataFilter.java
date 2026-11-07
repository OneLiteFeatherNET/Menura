package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import org.reactivestreams.Publisher;

/**
 * Marks every response of the prototype API as demo data. It runs outermost, so the refusals of the access rules
 * (401, 403) carry the header as well as the handlers' answers.
 */
@Filter("/api/prototype/**")
@Requires(env = "prototype")
public class PrototypeDemoDataFilter implements HttpServerFilter {

    static final String HEADER = "X-Menura-Demo-Data";

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.map(chain.proceed(request), response -> {
            response.header(HEADER, "true");
            return response;
        });
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }
}
