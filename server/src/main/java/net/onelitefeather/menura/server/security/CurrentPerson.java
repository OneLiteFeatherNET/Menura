package net.onelitefeather.menura.server.security;

import io.micronaut.http.HttpRequest;
import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.Optional;

/**
 * The seam between the access rules and sign-in. Given a request, it names the signed-in person and the channel,
 * or nothing when the request carries no valid session. The access filter and the controllers depend on this
 * interface only. The production implementation is {@link SessionCurrentPerson}, which maps the identity that the
 * authentication filter verified from the web session or a token. Test code may supply a fake of this interface.
 */
public interface CurrentPerson {

    Optional<Caller> resolve(HttpRequest<?> request);

    /** The caller of a request that must be signed in; anonymous requests answer 401. */
    default Caller require(HttpRequest<?> request) {
        return resolve(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }
}
