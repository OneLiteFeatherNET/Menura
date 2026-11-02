package net.onelitefeather.menura.core.paging;

import java.util.List;

/** Envelope of a cursor-paged response. {@code nextCursor} is null on the last page. */
public record Page<T>(List<T> items, String nextCursor) {
}
