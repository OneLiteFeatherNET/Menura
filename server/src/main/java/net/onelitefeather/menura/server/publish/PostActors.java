package net.onelitefeather.menura.server.publish;

import io.micronaut.http.HttpRequest;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.publish.PostActor;
import net.onelitefeather.menura.server.audit.Channel;
import net.onelitefeather.menura.server.auth.CurrentPerson;

/**
 * Turns the verified request into the actor of a post action, and parses the visibility of a request. The actor is
 * interactive only when the request came through the web application's own session: a token, the desktop app or an MCP
 * client never approves.
 */
final class PostActors {

    private PostActors() {
    }

    static PostActor of(HttpRequest<?> request) {
        CurrentPerson person = CurrentPerson.of(request)
                .orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
        return new PostActor(person.personId(), person.roles(), person.channel() == Channel.WEB);
    }

    static Visibility visibility(String value) {
        if (value == null) {
            return null;
        }
        for (Visibility visibility : Visibility.values()) {
            if (visibility.name().equalsIgnoreCase(value.strip())) {
                return visibility;
            }
        }
        throw new ProblemException(400, "Bad Request", "The visibility must be one of: private, unlisted, public.");
    }
}
