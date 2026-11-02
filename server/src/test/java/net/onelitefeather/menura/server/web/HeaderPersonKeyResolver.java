package net.onelitefeather.menura.server.web;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.util.Optional;

/** Test-only person resolution from the X-Test-Person header; replaces the sign-in based resolver. */
@Singleton
@Replaces(PrincipalPersonKeyResolver.class)
class HeaderPersonKeyResolver implements PersonKeyResolver {

    @Override
    public Optional<String> resolve(HttpRequest<?> request) {
        return request.getHeaders().getFirst("X-Test-Person");
    }
}
