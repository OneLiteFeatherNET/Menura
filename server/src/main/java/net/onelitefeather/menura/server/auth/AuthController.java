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
import net.onelitefeather.menura.core.problem.ProblemException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The backend-for-frontend of the web app. The browser starts a sign-in at {@code /auth/login}, which redirects it to
 * Entra ID through Menura's authorization server, and the callback turns the proven identity into a Menura token that
 * stays on the server. The browser receives only the {@code __Host-} session cookie. No token is ever returned.
 */
@Controller("/auth")
public class AuthController {

    static final Duration SIGN_IN_LIFETIME = Duration.ofMinutes(10);

    private final AuthSettings settings;
    private final EntraClient entra;
    private final MenuraTokenIssuer issuer;
    private final RoleMapping roles;
    private final AuthStore store;
    private final Clock clock;

    public AuthController(AuthSettings settings, EntraClient entra, MenuraTokenIssuer issuer, RoleMapping roles,
                          AuthStore store, Clock clock) {
        this.settings = settings;
        this.entra = entra;
        this.issuer = issuer;
        this.roles = roles;
        this.store = store;
        this.clock = clock;
    }

    /** The current person and their roles. Anonymous callers get 401. */
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
        String state = Secrets.generate();
        String nonce = Secrets.generate();
        String verifier = Pkce.newVerifier();
        store.saveSignIn(new AuthStore.SignIn(Secrets.hash(state), nonce, verifier, now.plus(SIGN_IN_LIFETIME)));
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
        AuthStore.SignIn signIn = store.takeSignIn(Secrets.hash(presentedState), now)
                .orElseThrow(this::unknownSignIn);

        EntraClient.UpstreamIdentity identity = entra.exchange(presentedCode, signIn.codeVerifier(), signIn.nonce());
        Set<Role> granted = roles.rolesFor(identity.groups());
        if (granted.isEmpty()) {
            throw new ProblemException(403, "Forbidden", "No Menura role is assigned to this account.");
        }
        MenuraTokenIssuer.IssuedToken token = issuer.issue(identity.subject(), granted);
        String secret = Secrets.generate();
        store.saveSession(new AuthStore.Session(Secrets.hash(secret), identity.subject(), token.token(),
                token.expiresAt()));
        Duration maxAge = Duration.between(now, token.expiresAt());
        return noStore(HttpResponse.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, settings.webAppUrl())
                .header(HttpHeaders.SET_COOKIE, SessionCookie.set(secret, maxAge)));
    }

    /** Ends the web session on the server and clears the browser cookie. */
    @Post("/logout")
    MutableHttpResponse<?> logout(HttpRequest<?> request) {
        Optional.ofNullable(request.getCookies().get(SessionCookie.NAME))
                .ifPresent(cookie -> store.deleteSession(Secrets.hash(cookie.getValue())));
        return noStore(HttpResponse.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, SessionCookie.clear()));
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
