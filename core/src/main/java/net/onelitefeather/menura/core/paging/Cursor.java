package net.onelitefeather.menura.core.paging;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque paging cursor: the URL-safe Base64 encoding of the sort key of the last item of the previous page.
 * Clients must treat it as opaque; only this class reads it.
 */
public final class Cursor {

    private Cursor() {
    }

    public static String encode(String sortKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sortKey.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the sort key of a cursor, or refuses a value that is not a cursor this service issued. */
    public static String decode(String cursor) {
        try {
            String key = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (key.isEmpty()) {
                throw invalid();
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ProblemException invalid() {
        return new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
    }
}
