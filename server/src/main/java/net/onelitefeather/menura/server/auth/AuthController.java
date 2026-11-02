package net.onelitefeather.menura.server.auth;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.secret.SecretGenerator;
import net.onelitefeather.menura.core.secret.SecretHasher;
import net.onelitefeather.menura.server.web.SessionCookie;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The backend-for-frontend of the web app. The browser starts a sign-in at {@code /auth/login}, which redirects it to
 * Entra ID through Menura's authorization server. The callback maps the proven identity to a person and a session;
 * the browser receives only the {@code __Host-menura-session} cookie, which holds an opaque secret. No token is ever
 * issued or stored for the web app.
 */
@Controller("/auth")
public class AuthController {

    static final Duration SIGN_IN_LIFETIME = Duration.ofMinutes(10);

    private final AuthSettings settings;
    private final EntraClient entra;
    private final RoleMapping roles;
    private final AuthStore store;
    private final Clock clock;
    private final SecretGenerator secrets;
    private final IdentifierGenerator identifiers;

    public AuthController(AuthSettings settings, EntraClient entra, RoleMapping roles, AuthStore store, Clock clock,
                          SecretGenerator secrets, IdentifierGenerator identifiers) {
        this.settings = settings;
        this.entra = entra;
        this.roles = roles;
        this.store = store;
        this.clock = clock;
        this.secrets = secrets;
        this.identifiers = identifiers;
    }

    /** The current person and their roles. Anonymous callers get 401 from the access filter before this runs. */
    @Get("/session")
    @RequiresRole
    SessionView session(HttpRequest<?> request) {
        CurrentPerson person = CurrentPerson.of(request)
                .orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to use this endpoint."));
        return new SessionView(person.subject(), person.roles().stream().map(Role::name).sorted().toList());
    }

    /** Starts a sign-in: records the transaction on the server and redirects the browser to Entra ID. */
    @Get("/login")
    @ExecuteOn(TaskExecutors.BLOCKING)
    MutableHttpResponse<?> login() {
        if (!entra.configured()) {
            throw new ProblemException(503, "Service Unavailable", "Sign-in with Entra ID is not configured.");
        }
        Instant now = clock.instant();
        String state = secrets.next();
        String nonce = secrets.next();
        String verifier = Pkce.newVerifier();
        store.saveSignIn(new AuthStore.SignIn(SecretHasher.hash(state), nonce, verifier,
                now.plus(SIGN_IN_LIFETIME)));
        URI target = entra.authorizeUri(state, nonce, Pkce.s256Challenge(verifier));
        return noStore(HttpResponse.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target.toString()));
    }

    /**
     * Completes a sign-in. The state must belong to a live transaction, which is then used up. The person needs at
     * least one Menura role, otherwise there is no session at all.
     */
    @Get("/callback")
    @ExecuteOn(TaskExecutors.BLOCKING)
    MutableHttpResponse<?> callback(@QueryValue Optional<String> code, @QueryValue Optional<String> state,
                                    @QueryValue Optional<String> error) {
        if (error.isPresent()) {
            throw new ProblemException(401, "Unauthorized", "Entra ID did not complete the sign-in.");
        }
        String presentedState = state.orElseThrow(this::unknownSignIn);
        String presentedCode = code.orElseThrow(this::unknownSignIn);
        Instant now = clock.instant();
        AuthStore.SignIn signIn = store.takeSignIn(SecretHasher.hash(presentedState), now)
                .orElseThrow(this::unknownSignIn);

        EntraClient.UpstreamIdentity identity = entra.exchange(presentedCode, signIn.codeVerifier(), signIn.nonce());
        Set<Role> granted = roles.rolesFor(identity.groups());
        if (granted.isEmpty()) {
            throw new ProblemException(403, "Forbidden", "No Menura role is assigned to this account.");
        }
        UUID person = store.findOrCreatePerson(settings.entraIssuer(), identity.subject(), identity.displayName(),
                identifiers.next(), now);
        String secret = secrets.next();
        Duration lifetime = settings.sessionLifetime();
        store.saveSession(new AuthStore.Session(SecretHasher.hash(secret), person, identity.subject(), granted, now,
                now.plus(lifetime), now));
        return noStore(HttpResponse.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, settings.webAppUrl())
                .header(HttpHeaders.SET_COOKIE, SessionCookie.set(secret, lifetime)));
    }

    /** Ends the web session on the server and clears the browser cookie. Works without a session too. */
    @Post("/logout")
    MutableHttpResponse<?> logout(HttpRequest<?> request) {
        Optional.ofNullable(request.getCookies().get(SessionCookie.NAME))
                .ifPresent(cookie -> store.deleteSession(SecretHasher.hash(cookie.getValue())));
        return noStore(HttpResponse.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, SessionCookie.expire()));
    }

    private ProblemException unknownSignIn() {
        return new ProblemException(400, "Bad Request",
                "The sign-in is unknown, has expired or was already used. Start it again.");
    }

    private static MutableHttpResponse<?> noStore(MutableHttpResponse<?> response) {
        return response.header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** The body of {@code GET /auth/session}: the subject and roles, never a token. */
    @Serdeable
    public record SessionView(String subject, List<String> roles) {
    }
}
