package net.onelitefeather.menura.server.post;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.paging.Cursor;
import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostService;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.post.ExternalPostViews.ExternalPostPage;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The posts of a channel: imported from its history or published by Menura, with their status and the time of the last
 * sync. Read by every signed-in person, as the channel itself is. A channel that does not exist answers 404.
 */
@Controller("/channels")
public class ExternalPostController {

    private static final String SEPARATOR = "|";

    private final ExternalPostService posts;

    public ExternalPostController(ExternalPostService posts) {
        this.posts = posts;
    }

    @Get("/{id}/external-posts")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    ExternalPostPage list(@PathVariable UUID id, @QueryValue Optional<Integer> limit,
                          @QueryValue Optional<String> cursor) throws SQLException {
        PageRequest page = PageRequest.of(limit.orElse(null), cursor.orElse(null));
        Instant afterPublishedAt = null;
        UUID afterId = null;
        if (page.afterKey() != null) {
            String[] parts = page.afterKey().split("\\" + SEPARATOR, 2);
            try {
                afterPublishedAt = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (RuntimeException notACursor) {
                throw new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
            }
        }
        ExternalPostService.Overview overview = posts.overview(id, afterPublishedAt, afterId, page.limit() + 1);
        List<ExternalPost> rows = overview.posts();
        boolean more = rows.size() > page.limit();
        List<ExternalPost> items = more ? rows.subList(0, page.limit()) : rows;
        String next = null;
        if (more) {
            ExternalPost last = items.get(items.size() - 1);
            next = Cursor.encode(last.publishedAt() + SEPARATOR + last.id());
        }
        return ExternalPostViews.page(overview, items, next);
    }
}
