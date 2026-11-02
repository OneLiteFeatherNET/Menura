package net.onelitefeather.menura.server.problem;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import net.onelitefeather.menura.core.problem.ProblemException;

/** Test-only route that raises the two kinds of error the exception handling must map. */
@Controller("/test-problem")
class ProblemTestController {

    @Get("/missing")
    String missing() {
        throw new ProblemException(404, "Not Found", "No test item has this id.");
    }

    @Get("/boom")
    String boom() {
        throw new IllegalStateException("secret internal detail");
    }
}
