package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.util.List;
import java.util.Optional;

/** The shorts of the prototype. Reads need any signed-in role; assigning a series needs EDITOR or ADMIN. */
@Controller("/api/prototype/posts")
@Requires(env = "prototype")
public class PrototypePostController {

    private final DemoStore store;

    public PrototypePostController(DemoStore store) {
        this.store = store;
    }

    @Get
    @RequiresRole
    List<Model.Post> list(@QueryValue Optional<String> seriesId, @QueryValue Optional<String> channelId) {
        return store.posts(seriesId.orElse(null), channelId.orElse(null));
    }

    @Put("/{id}/series")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    Model.Post assign(@PathVariable String id, @Body Model.AssignRequest body) {
        return store.assign(id, body);
    }
}
