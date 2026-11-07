package net.onelitefeather.menura.server.prototype;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import net.onelitefeather.menura.server.auth.RequiresRole;

import java.util.List;
import java.util.Optional;

/** Coverage, attribution, comparison and warnings of the prototype. Reads need any signed-in role. */
@Controller("/api/prototype")
@Requires(env = "prototype")
public class PrototypeReportController {

    private final DemoStore store;

    public PrototypeReportController(DemoStore store) {
        this.store = store;
    }

    @Get("/coverage")
    @RequiresRole
    List<Model.Coverage> coverage() {
        return store.coverage();
    }

    @Get("/attribution/weekly")
    @RequiresRole
    List<Model.WeeklyAttribution> attribution() {
        return store.attribution();
    }

    @Get("/series-comparison")
    @RequiresRole
    List<Model.SeriesComparison> comparison(@QueryValue Optional<String> week) {
        return store.comparison(week.orElse(null));
    }

    @Get("/warnings")
    @RequiresRole
    List<Model.Warning> warnings() {
        return store.warnings();
    }
}
