package net.onelitefeather.menura.core.channel.youtube;

import net.onelitefeather.menura.core.channel.HttpTransport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A scripted YouTube endpoint that answers by method, path and query parameters, so one path can answer several pages or
 * parts differently. Bodies are hand-written JSON files under {@code src/test/resources/youtube}. A request no route matches
 * fails the test, and every request is recorded.
 */
final class RecordedYouTube implements HttpTransport {

    /** One recorded request. */
    record Sent(String method, URI uri) {
    }

    private record Route(String method, String path, Predicate<Map<String, String>> query, Reply reply) {
    }

    private final List<Route> routes = new ArrayList<>();
    private final List<Sent> sent = new ArrayList<>();

    /** Answers every request of the method and path with the reply. */
    RecordedYouTube on(String method, String path, Reply reply) {
        return when(method, path, query -> true, reply);
    }

    /** Answers the requests of the method and path whose query matches the predicate. The first matching route wins. */
    RecordedYouTube when(String method, String path, Predicate<Map<String, String>> query, Reply reply) {
        routes.add(new Route(method, path, query, reply));
        return this;
    }

    @Override
    public Reply send(String method, URI uri, String bearerToken, String formBody) {
        sent.add(new Sent(method, uri));
        Map<String, String> query = query(uri);
        for (Route route : routes) {
            if (route.method().equals(method) && route.path().equals(uri.getPath()) && route.query().test(query)) {
                return route.reply();
            }
        }
        throw new AssertionError("unscripted request " + method + " " + uri.getPath() + " " + uri.getRawQuery());
    }

    List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** The text of a JSON fixture under {@code src/test/resources/youtube}. */
    static String json(String name) {
        try (InputStream in = RecordedYouTube.class.getResourceAsStream("/youtube/" + name)) {
            if (in == null) {
                throw new AssertionError("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The decoded query parameters of a request; a parameter without a value maps to the empty string. */
    static Map<String, String> query(URI uri) {
        Map<String, String> params = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = java.net.URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
