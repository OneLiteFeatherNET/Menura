package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.util.List;

/** The content series of the prototype. Reads need any signed-in role; creating a series needs EDITOR or ADMIN. */
@Controller("/api/prototype/series")
@Requires(env = "prototype")
public class PrototypeSeriesController {

    private final DemoStore store;

    public PrototypeSeriesController(DemoStore store) {
        this.store = store;
    }

    @Get
    @RequiresRole
    List<Model.Series> list() {
        return store.series();
    }

    @Post
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    HttpResponse<Model.Series> create(@Body Model.SeriesRequest body) {
        return HttpResponse.status(HttpStatus.CREATED).body(store.createSeries(body));
    }
}
