package net.onelitefeather.menura.server.auth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.web.SessionCookie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only sign-in for the end-to-end suite. It exists only in environment {@code e2e}: outside it the bean is absent
 * and so is the route. It skips the upstream Entra ID step and nothing after it: the session it creates is stored and
 * sent exactly like one from the callback, so the role checks and the cookie are the production code. The role is
 * chosen by the test and granted to a new person, so no two tests share a session. The cookie value is never logged.
 */
@Requires(env = "e2e")
@Controller("/auth/e2e")
public class E2eSignInController {

    /** The issuer of the test identities; it is not Entra ID. */
    static final String ISSUER = "urn:menura:e2e";

    private final AuthSettings settings;
    private final AuthStore store;
    private final Clock clock;
    private final SecretGenerator secrets;
    private final IdentifierGenerator identifiers;

    public E2eSignInController(AuthSettings settings, AuthStore store, Clock clock, SecretGenerator secrets,
                               IdentifierGenerator identifiers) {
        this.settings = settings;
        this.store = store;
        this.clock = clock;
        this.secrets = secrets;
        this.identifiers = identifiers;
    }

    /** Signs in as a new test person with the given role, then redirects the browser to the web app. */
    @Get("/sign-in")
    @ExecuteOn(TaskExecutors.BLOCKING)
    MutableHttpResponse<?> signIn(@QueryValue String role) {
        Role chosen = Role.parse(role).orElseThrow(() -> new ProblemException(400, "Bad Request",
                "The test role is not one of viewer, editor, publisher, operator or admin."));
        Instant now = clock.instant();
        String subject = "e2e-" + chosen.name().toLowerCase() + "-" + identifiers.next();
        UUID person = store.findOrCreatePerson(ISSUER, subject, "E2E " + chosen.name(), identifiers.next(), now);
        String secret = secrets.next();
        Duration lifetime = settings.sessionLifetime();
        store.saveSession(new AuthStore.Session(SecretHasher.hash(secret), person, subject, Set.of(chosen), now,
                now.plus(lifetime), now));
        return noStore(HttpResponse.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, settings.webAppUrl())
                .header(HttpHeaders.SET_COOKIE, SessionCookie.set(secret, lifetime)));
    }

    private static MutableHttpResponse<?> noStore(MutableHttpResponse<?> response) {
        return response.header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
