package net.onelitefeather.menura.server.web;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.time.Duration;

/**
 * Test-only routes. {@code /test-web/session} is the fake session endpoint: it sets the session cookie the way the
 * real sign-in will, through {@link SessionCookie}, so the cookie attributes are asserted on the wire.
 */
@Controller("/test-web")
class WebTestController {

    @Get("/ok")
    String ok() {
        return "ok";
    }

    @Post("/session")
    HttpResponse<String> session() {
        return HttpResponse.ok("{}")
                .header("Set-Cookie", SessionCookie.set("opaque-session-value", Duration.ofHours(8)));
    }
}
