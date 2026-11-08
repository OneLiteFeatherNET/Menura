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

    /**
     * Sends one request with explicit headers and a binary body, and returns the headers the resumable upload protocol
     * reads: the session location and the confirmed byte range. Only the publishing adapters use it.
     */
    default Answer call(String method, URI uri, String bearerToken, java.util.Map<String, String> headers, byte[] body)
            throws IOException {
        throw new UnsupportedOperationException("raw requests are not available in this transport");
    }

    /** A raw answer: status, body and the Location, Range and Retry-After headers when they were sent. */
    record Answer(int status, String body, Optional<String> location, Optional<String> range,
                  Optional<String> retryAfter) {
    }

    /** A platform's answer: status, body and the Retry-After header when it sent one. */
    record Reply(int status, String body, Optional<String> retryAfter) {

        public Reply(int status, String body) {
            this(status, body, Optional.empty());
        }
    }
}
