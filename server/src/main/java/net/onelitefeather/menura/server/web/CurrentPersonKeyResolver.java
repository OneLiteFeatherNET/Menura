package net.onelitefeather.menura.server.web;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.auth.CurrentPerson;

import java.util.Optional;

/** Names the person of a request by Menura's person id, as set by the authentication filter. */
@Singleton
public class CurrentPersonKeyResolver implements PersonKeyResolver {

    @Override
    public Optional<String> resolve(HttpRequest<?> request) {
        return CurrentPerson.of(request).map(person -> person.personId().toString());
    }
}
