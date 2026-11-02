package net.onelitefeather.menura.server.logging;

import java.util.regex.Pattern;

/**
 * Removes secrets from text before it is written to a log (principle 11: secrets never appear in logs).
 * Covers Authorization and cookie headers, bearer tokens, the session cookie value, token-bearing query
 * parameters and presigned URL signatures.
 */
public final class LogRedaction {

    static final String MASK = "[REDACTED]";

    private static final Pattern HEADER = Pattern.compile(
            "(?i)\\b(Authorization|Proxy-Authorization|Cookie|Set-Cookie)(\\s*[:=]\\s*)[^\\r\\n]*");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern SESSION_COOKIE = Pattern.compile("__Host-menura_session=[^;\\s]*");
    private static final Pattern SECRET_PARAMETER = Pattern.compile(
            "(?i)([?&;\\s]|^)(token|access_token|refresh_token|id_token|feed_token|code|scene|secret|password|"
                    + "key|X-Amz-Signature|X-Amz-Credential|X-Amz-Security-Token)=[^&\\s;]*");

    private LogRedaction() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String result = HEADER.matcher(text).replaceAll("$1$2" + MASK);
        result = BEARER.matcher(result).replaceAll("Bearer " + MASK);
        result = SESSION_COOKIE.matcher(result).replaceAll("__Host-menura_session=" + MASK);
        result = SECRET_PARAMETER.matcher(result).replaceAll("$1$2=" + MASK);
        return result;
    }

    /** The path of a request URI, with the query string replaced by a marker so no parameter value is logged. */
    public static String redactUri(String uri) {
        int query = uri.indexOf('?');
        return query < 0 ? uri : uri.substring(0, query) + "?" + MASK;
    }
}
