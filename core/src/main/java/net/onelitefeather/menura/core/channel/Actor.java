package net.onelitefeather.menura.core.channel;

import java.util.UUID;

/** The person behind a connection action, the channel the request came in on and the request id. */
public record Actor(UUID personId, String channel, String requestId) {
}
