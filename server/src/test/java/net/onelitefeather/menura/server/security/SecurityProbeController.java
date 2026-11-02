package net.onelitefeather.menura.server.security;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import net.onelitefeather.menura.core.problem.ProblemException;

/** Test-only routes that refuse with the status codes the access rules must render as Problem Details. */
@Controller("/test-security")
class SecurityProbeController {

    @Get("/forbidden")
    String forbidden() {
        throw new ProblemException(403, "Forbidden", "Your roles do not allow this action.");
    }

    @Get("/rate-limited")
    String rateLimited() {
        throw new ProblemException(429, "Too Many Requests", "Too many requests; retry later.");
    }
}
