package net.onelitefeather.menura.server.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.server.audit.Channel;

import java.util.Optional;
import java.util.UUID;

/**
 * Test sign-in: the caller is the person named in the {@code X-Test-Person} header, on the channel named in
 * {@code X-Test-Channel} (default web). Without the person header the request is anonymous.
 */
@Singleton
@Replaces(UnauthenticatedCurrentPerson.class)
public class HeaderCurrentPerson implements CurrentPerson {

    public static final String PERSON_HEADER = "X-Test-Person";
    public static final String CHANNEL_HEADER = "X-Test-Channel";

    @Override
    public Optional<Caller> resolve(HttpRequest<?> request) {
        return request.getHeaders().getFirst(PERSON_HEADER)
                .map(value -> new Caller(UUID.fromString(value), channelOf(request)));
    }

    private static Channel channelOf(HttpRequest<?> request) {
        return request.getHeaders().getFirst(CHANNEL_HEADER)
                .map(value -> Channel.valueOf(value.toUpperCase()))
                .orElse(Channel.WEB);
    }
}
