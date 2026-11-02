package net.onelitefeather.menura.server.security;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * The production default until sign-in is wired: no request carries a person, so every protected route answers 401.
 * The access seam fails closed. The sign-in change replaces this bean.
 */
@Singleton
public class UnauthenticatedCurrentPerson implements CurrentPerson {

    @Override
    public Optional<Caller> resolve(HttpRequest<?> request) {
        return Optional.empty();
    }
}
