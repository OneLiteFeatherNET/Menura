package net.onelitefeather.menura.server.prototype.pool;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.AssetDetail;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.CreateRequest;
import net.onelitefeather.menura.server.prototype.pool.PoolModel.TagsRequest;

import java.util.List;
import java.util.Optional;

/**
 * The pool of the prototype: people, assets with their transcripts and clips, tag edits and simulated uploads. Reads
 * need any signed-in role; tag edits and uploads need EDITOR or ADMIN. The rules live in {@link PoolService}.
 */
@Controller("/api/prototype")
@Requires(env = "prototype")
public class PrototypePoolController {

    private final PoolService pool;

    public PrototypePoolController(PoolService pool) {
        this.pool = pool;
    }

    @Get("/people")
    @RequiresRole
    List<Person> people() {
        return pool.people();
    }

    @Get("/assets")
    @RequiresRole
    List<Asset> list(HttpRequest<?> request,
                     @QueryValue Optional<String> kind,
                     @QueryValue Optional<String> ownerId,
                     @QueryValue Optional<String> personId,
                     @QueryValue Optional<String> tag,
                     @QueryValue Optional<String> stage,
                     @QueryValue Optional<String> q) {
        PoolService.Query query = new PoolService.Query(text(kind), text(ownerId), text(personId), text(tag),
                text(stage), text(q));
        return pool.list(query, CurrentPerson.of(request).map(CurrentPerson::subject));
    }

    @Get("/assets/{id}")
    @RequiresRole
    AssetDetail detail(@PathVariable String id) {
        return pool.detail(id);
    }

    @Put("/assets/{id}/tags")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    Asset updateTags(HttpRequest<?> request, @PathVariable String id, @Body TagsRequest body) {
        return pool.updateTags(id, body, subject(request));
    }

    @Post("/assets")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    HttpResponse<Asset> create(HttpRequest<?> request, @Body CreateRequest body) {
        return HttpResponse.status(HttpStatus.CREATED).body(pool.create(body, subject(request)));
    }

    /** The subject of a write. The access check has already refused an anonymous caller; this is a second guard. */
    private static String subject(HttpRequest<?> request) {
        return CurrentPerson.of(request).map(CurrentPerson::subject)
                .orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to use this endpoint."));
    }

    /** A query value with blanks treated as absent. */
    private static String text(Optional<String> value) {
        return value.map(String::trim).filter(candidate -> !candidate.isEmpty()).orElse(null);
    }
}
