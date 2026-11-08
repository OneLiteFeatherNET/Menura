package net.onelitefeather.menura.core.channel;

import java.time.Duration;

/**
 * A failure reported by a platform or by reaching it. The message is fixed text written by the adapter; it never
 * contains a token, a code or a response body.
 */
public class PlatformException extends RuntimeException {

    /** How the platform answered, which decides what Menura does next. */
    public enum Kind {
        /** The platform refused the grant or the request: an invalid, revoked or expired token or code. */
        REJECTED,
        /** The platform or the network failed; the grant may still be good. */
        UNAVAILABLE,
        /** The platform's quota or rate limit blocked the request. */
        RATE_LIMITED
    }

    private final Kind kind;
    private final Duration retryAfter;

    public PlatformException(Kind kind, String message, Duration retryAfter) {
        super(message);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    /** The platform's own retry hint, when it gave one. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
