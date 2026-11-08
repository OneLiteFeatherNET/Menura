package net.onelitefeather.menura.server.prototype.schedule;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.prototype.schedule.PlatformCapabilities.Capability;
import net.onelitefeather.menura.server.prototype.schedule.ScheduleService.CreateRequest;
import net.onelitefeather.menura.server.prototype.schedule.ScheduleService.PlanRequest;
import net.onelitefeather.menura.server.prototype.schedule.ScheduleService.UpdateRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The publishing calendar of the prototype. Reads need any signed-in role; creating, moving, deleting and planning
 * need EDITOR or ADMIN, and the role check runs before any rule.
 */
@Controller("/api/prototype")
@Requires(env = "prototype")
public class PrototypeScheduleController {

    private final ScheduleService service;

    public PrototypeScheduleController(ScheduleService service) {
        this.service = service;
    }

    @Get("/platforms")
    @RequiresRole
    Map<String, Capability> platforms() {
        return service.platforms();
    }

    @Get("/publications")
    @RequiresRole
    List<Publication> list(@QueryValue Optional<String> from, @QueryValue Optional<String> to,
                           @QueryValue Optional<String> channelId, @QueryValue Optional<String> seriesId,
                           @QueryValue Optional<String> status) {
        return service.list(from.orElse(null), to.orElse(null), channelId.orElse(null), seriesId.orElse(null),
                status.orElse(null));
    }

    @Post("/publications")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    HttpResponse<List<Publication>> create(@Body CreateRequest body) {
        return HttpResponse.status(HttpStatus.CREATED).body(service.create(body));
    }

    @Put("/publications/{id}")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    Publication update(@PathVariable String id, @Body UpdateRequest body) {
        return service.update(id, body);
    }

    @Delete("/publications/{id}")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    HttpResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return HttpResponse.noContent();
    }

    @Post("/series/{id}/plan")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    HttpResponse<List<Publication>> plan(@PathVariable String id, @Body PlanRequest body) {
        return HttpResponse.status(HttpStatus.CREATED).body(service.plan(id, body));
    }
}
