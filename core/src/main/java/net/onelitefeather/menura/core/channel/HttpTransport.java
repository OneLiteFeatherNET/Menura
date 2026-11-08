package net.onelitefeather.menura.core.channel;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * The HTTP seam of the platform adapters. Production uses {@link JdkHttpTransport}; tests supply a scripted fake so no
 * test reaches a real platform.
 */
public interface HttpTransport {

    /** Sends one request. Throws {@link IOException} when the platform cannot be reached. */
    Reply send(String method, URI uri, String bearerToken, String formBody) throws IOException;

    /** A platform's answer: status, body and the Retry-After header when it sent one. */
    record Reply(int status, String body, Optional<String> retryAfter) {

        public Reply(int status, String body) {
            this(status, body, Optional.empty());
        }
    }
}
