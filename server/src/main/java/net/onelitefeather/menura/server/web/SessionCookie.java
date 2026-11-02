package net.onelitefeather.menura.server.web;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * The one place that writes the web session cookie (principle 6, design: host-only, Secure, HttpOnly,
 * SameSite=Strict). The name carries the {@code __Host-} prefix, which forces Secure, Path=/ and no Domain.
 */
public final class SessionCookie {

    public static final String NAME = "__Host-menura-session";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._~-]+");

    private SessionCookie() {
    }

    /** The Set-Cookie value that sets the session. The value is an opaque session id, never a token. */
    public static String set(String value, Duration maxAge) {
        if (value == null || !SAFE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("session value must be non-empty and contain only unreserved characters");
        }
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("session lifetime must be positive");
        }
        return NAME + "=" + value + attributes(maxAge.getSeconds());
    }

    /** The Set-Cookie value that clears the session on the same name and attributes. */
    public static String expire() {
        return NAME + "=" + attributes(0);
    }

    private static String attributes(long maxAgeSeconds) {
        return "; Max-Age=" + maxAgeSeconds + "; Path=/; Secure; HttpOnly; SameSite=Strict";
    }
}
