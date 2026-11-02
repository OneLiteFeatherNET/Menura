package net.onelitefeather.menura.server.web;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.security.Principal;
import java.util.Optional;

/** Uses the authenticated principal of the request, which the sign-in sets from the validated token. */
@Singleton
public class PrincipalPersonKeyResolver implements PersonKeyResolver {

    @Override
    public Optional<String> resolve(HttpRequest<?> request) {
        return request.getUserPrincipal(Principal.class).map(Principal::getName);
    }
}
