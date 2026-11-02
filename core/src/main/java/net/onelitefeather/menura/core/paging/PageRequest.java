package net.onelitefeather.menura.core.paging;

import net.onelitefeather.menura.core.problem.ProblemException;

/**
 * A cursor-paged request: {@code limit} items (default 50, at most 200) after the position named by the
 * cursor, or from the first item when there is no cursor.
 */
public record PageRequest(int limit, String afterKey) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public PageRequest {
        if (limit < 1) {
            throw new ProblemException(400, "Invalid limit", "The limit must be at least 1.");
        }
    }

    public static PageRequest of(Integer requestedLimit, String cursor) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        String afterKey = cursor == null ? null : Cursor.decode(cursor);
        return new PageRequest(limit, afterKey);
    }
}
