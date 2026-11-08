package net.onelitefeather.menura.server.brand;

import io.micronaut.http.sse.Event;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.brand.BrandKitEvents;
import net.onelitefeather.menura.core.brand.BrandKitEvents.KitPublished;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * Delivers the publication notifications of the brand kit over Server-Sent Events. Subscribers receive only what is
 * published while they are connected; a client that reconnects reads the current version instead (principle 3). A
 * heartbeat keeps every stream below the proxy's idle limit (principle 15).
 */
@Singleton
public class BrandKitNotifications implements BrandKitEvents {

    static final String PUBLISHED = "brand-kit.published";
    static final String HEARTBEAT = "heartbeat";
    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

    private final Sinks.Many<Event<String>> published = Sinks.many().multicast().directBestEffort();

    @Override
    public void published(KitPublished event) {
        published.tryEmitNext(Event.of("{\"version\":" + event.version() + "}")
                .name(PUBLISHED)
                .id(String.valueOf(event.version())));
    }

    /** The notifications of this process, interleaved with a heartbeat. */
    public Flux<Event<String>> stream() {
        Flux<Event<String>> heartbeats = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> Event.of("").name(HEARTBEAT));
        return Flux.merge(published.asFlux(), heartbeats);
    }
}
