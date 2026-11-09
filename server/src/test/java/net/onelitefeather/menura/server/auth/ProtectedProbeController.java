package net.onelitefeather.menura.server.auth;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.auth.Role;

import java.util.ArrayList;
import java.util.List;

/** Test-only endpoints that declare their roles, so the role check can be observed from outside. */
@Controller("/test-auth")
class ProtectedProbeController {

    private final HandlerCalls calls;

    ProtectedProbeController(HandlerCalls calls) {
        this.calls = calls;
    }

    @Get("/editor")
    @RequiresRole(Role.EDITOR)
    String editor() {
        calls.record("editor");
        return "editor";
    }

    @Post("/write")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    String write() {
        calls.record("write");
        return "write";
    }

    @Delete("/write")
    @RequiresRole({Role.EDITOR, Role.ADMIN})
    String remove() {
        calls.record("remove");
        return "remove";
    }

    @Get("/signed-in")
    @RequiresRole
    String signedIn() {
        calls.record("signed-in");
        return "signed-in";
    }

    /** Records which handlers ran, so tests can prove a refused request never reached one. */
    @Singleton
    static class HandlerCalls {

        private final List<String> recorded = new ArrayList<>();

        synchronized void record(String name) {
            recorded.add(name);
        }

        synchronized List<String> calls() {
            return List.copyOf(recorded);
        }

        synchronized void clear() {
            recorded.clear();
        }
    }
}
