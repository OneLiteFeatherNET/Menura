package net.onelitefeather.menura.server.web;

import io.micronaut.http.HttpRequest;

import java.util.Optional;

/** Names the person behind a request for the person budget. An anonymous request names nobody. */
public interface PersonKeyResolver {

    Optional<String> resolve(HttpRequest<?> request);
}
