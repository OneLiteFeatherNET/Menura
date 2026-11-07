package net.onelitefeather.menura.server.prototype;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps a host that a join came in through to a channel. Matching is on the normalised host: trimmed, lower case,
 * without a port and without trailing dots. A host that matches no channel (including the plain domain) is
 * "direct".
 */
final class Hosts {

    private Hosts() {
    }

    static String normalize(String raw) {
        String host = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int port = host.lastIndexOf(':');
        if (port >= 0) {
            host = host.substring(0, port);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    static Optional<String> channelIdFor(List<Model.Channel> channels, String host) {
        String wanted = normalize(host);
        return channels.stream()
                .filter(channel -> normalize(channel.joinHost()).equals(wanted))
                .map(Model.Channel::id)
                .findFirst();
    }
}
