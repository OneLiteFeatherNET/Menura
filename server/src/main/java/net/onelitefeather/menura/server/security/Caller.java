package net.onelitefeather.menura.server.security;

import net.onelitefeather.menura.server.audit.Channel;

import java.util.UUID;

/** The signed-in person behind a request and the channel the request came in on. */
public record Caller(UUID personId, Channel channel) {
}
