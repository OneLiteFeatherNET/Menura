package net.onelitefeather.menura.core.channel.support;

import net.onelitefeather.menura.core.channel.HttpTransport;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fake platform endpoint: a scripted HTTP transport. Each route is a method and a path; a request to an unscripted
 * route fails the test, so no test reaches an unplanned endpoint. Every request is recorded for assertions.
 */
public final class ScriptedPlatform implements HttpTransport {

    /** One recorded request: what the adapter sent. */
    public record Sent(String method, URI uri, String bearerToken, String formBody) {
    }

    private final Map<String, Object> routes = new ConcurrentHashMap<>();
    private final List<Sent> sent = new ArrayList<>();

    /** Answers the route with a reply; {@code failure} makes the route unreachable instead. */
    public ScriptedPlatform route(String method, String path, Reply reply) {
        routes.put(method + " " + path, reply);
        return this;
    }

    public ScriptedPlatform unreachable(String method, String path, IOException failure) {
        routes.put(method + " " + path, failure);
        return this;
    }

    @Override
    public Reply send(String method, URI uri, String bearerToken, String formBody) throws IOException {
        sent.add(new Sent(method, uri, bearerToken, formBody));
        Object scripted = routes.get(method + " " + uri.getPath());
        if (scripted instanceof IOException failure) {
            throw failure;
        }
        if (scripted == null) {
            throw new AssertionError("unscripted request " + method + " " + uri.getHost() + uri.getPath());
        }
        return (Reply) scripted;
    }

    /** Forgets the scripted routes and the recorded requests. */
    public void clear() {
        routes.clear();
        sent.clear();
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public Optional<Sent> last(String path) {
        return sent.stream().filter(request -> request.uri().getPath().equals(path)).reduce((first, second) -> second);
    }
}
