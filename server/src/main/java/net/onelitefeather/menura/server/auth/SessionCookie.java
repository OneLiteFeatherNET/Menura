package net.onelitefeather.menura.server.auth;

import java.time.Duration;

/**
 * The web session cookie. The {@code __Host-} prefix makes the browser accept it only with Secure, Path=/ and no
 * Domain attribute, so it is host-only on the API host. It is HttpOnly (scripts cannot read it) and SameSite=Strict.
 */
final class SessionCookie {

    static final String NAME = "__Host-menura-session";

    private static final String ATTRIBUTES = "; Path=/; Secure; HttpOnly; SameSite=Strict";

    private SessionCookie() {
    }

    /** The Set-Cookie value that gives the browser the secret for the given lifetime. */
    static String set(String secret, Duration maxAge) {
        return NAME + "=" + secret + "; Max-Age=" + maxAge.toSeconds() + ATTRIBUTES;
    }

    /** The Set-Cookie value that removes the cookie from the browser. */
    static String clear() {
        return NAME + "=; Max-Age=0" + ATTRIBUTES;
    }
}
