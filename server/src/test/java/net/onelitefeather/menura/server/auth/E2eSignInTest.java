package net.onelitefeather.menura.server.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The test-only sign-in of environment e2e: the cookie it issues is an ordinary session cookie. */
@MicronautTest(environments = "e2e", rebuildContext = true)
@Property(name = "menura.test.in-memory-auth", value = "true")
@Property(name = "micronaut.http.client.follow-redirects", value = "false")
class E2eSignInTest {

    static final String COOKIE = "__Host-menura-session";
    static final Pattern COOKIE_VALUE = Pattern.compile("__Host-menura-session=([^;]+)");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    Clock clock;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        rootLogger().addAppender(logs);
    }

    @AfterEach
    void stopCapture() {
        rootLogger().detachAppender(logs);
        logs.stop();
    }

    @Test
    void signInAsEditorSetsHostOnlySessionCookieAndRedirectsToTheWebApp() {
        var response = signIn("editor");

        assertEquals(HttpStatus.FOUND, response.getStatus());
        String setCookie = response.header("Set-Cookie");
        assertTrue(setCookie.startsWith(COOKIE + "="), "the e2e sign-in sets the __Host- session cookie");
        assertTrue(setCookie.contains("; Secure; HttpOnly; SameSite=Strict"), "the cookie has the production attributes");
        assertFalse(setCookie.contains("Domain="), "the cookie is host-only");
    }

    @Test
    void sessionCookieIssuedForEditorIsAcceptedAsAnEditorSession() {
        String cookie = sessionCookie(signIn("editor"));

        var session = client.toBlocking().retrieve(
                HttpRequest.GET("/auth/session").cookie(Cookie.of(COOKIE, cookie)), Map.class);

        assertEquals(List.of("EDITOR"), session.get("roles"), "the session carries exactly the chosen role");
    }

    @Test
    void unknownRoleIsRefusedWithAProblemDetailsBadRequest() {
        var refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/auth/e2e/sign-in?role=superuser"), String.class));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void signInDoesNotLogTheCookieValue() {
        String cookie = sessionCookie(signIn("viewer"));

        String everything = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertFalse(everything.contains(cookie), "the log must never hold the session cookie value");
    }

    @Test
    void environmentE2eUsesAFixedClock() {
        assertEquals(Instant.parse("2026-11-09T10:00:00Z"), clock.instant(),
                "the e2e environment injects the fixed clock, so tests see the same today");
    }

    private io.micronaut.http.HttpResponse<String> signIn(String role) {
        return client.toBlocking().exchange(HttpRequest.GET("/auth/e2e/sign-in?role=" + role), String.class);
    }

    private static String sessionCookie(io.micronaut.http.HttpResponse<?> response) {
        Matcher matcher = COOKIE_VALUE.matcher(response.header("Set-Cookie"));
        assertTrue(matcher.find(), "the response carries the session cookie");
        return matcher.group(1);
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
