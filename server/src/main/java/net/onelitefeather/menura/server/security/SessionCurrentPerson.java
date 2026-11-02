package net.onelitefeather.menura.server.security;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.auth.CurrentPerson;

import java.util.Optional;

/**
 * The production {@link net.onelitefeather.menura.server.security.CurrentPerson}: the caller is the person the
 * authentication filter verified from the web session or a bearer token. Without such a person the request is
 * anonymous and the access filter refuses it, so the seam fails closed.
 */
@Singleton
public class SessionCurrentPerson implements net.onelitefeather.menura.server.security.CurrentPerson {

    @Override
    public Optional<Caller> resolve(HttpRequest<?> request) {
        return CurrentPerson.of(request).map(person -> new Caller(person.personId(), person.channel()));
    }
}
