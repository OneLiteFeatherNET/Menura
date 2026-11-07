package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.util.List;

/** Brand kit, channels and the render bundle of the prototype. Reads need any signed-in role. */
@Controller("/api/prototype")
@Requires(env = "prototype")
public class PrototypeCatalogController {

    private final DemoStore store;

    public PrototypeCatalogController(DemoStore store) {
        this.store = store;
    }

    @Get("/brand-kit")
    @RequiresRole
    Model.BrandKit brandKit() {
        return store.brandKit();
    }

    @Get("/channels")
    @RequiresRole
    List<Model.Channel> channels() {
        return store.channels();
    }

    @Get("/render-bundle")
    @RequiresRole
    Model.RenderBundle renderBundle() {
        return store.renderBundle();
    }
}
