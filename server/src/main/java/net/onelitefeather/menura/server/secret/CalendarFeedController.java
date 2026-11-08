package net.onelitefeather.menura.server.secret;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.problem.ProblemDetails;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.publish.IcsFeed;
import net.onelitefeather.menura.core.publish.PostService;
import net.onelitefeather.menura.server.problem.ProblemResponse;
import net.onelitefeather.menura.server.security.SelfAuthenticated;
import net.onelitefeather.menura.server.web.PersonRateLimiter;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The calendar feed a calendar client subscribes to (change add-content-distribution). The token in the path is the
 * credential, so the route is {@link SelfAuthenticated}. The feed is read-only: it has no route for any other method,
 * so a write request is refused without a change. An unknown, revoked, rotated or malformed token answers 404 with the
 * same body, so the feed does not reveal which tokens existed. Each token has its own request budget and answers 429
 * with Retry-After when it is used up.
 */
@Controller("/calendar/feed")
public class CalendarFeedController {

    /** Requests one token may make in one window. Calendar clients poll rarely; this only stops a runaway client. */
    static final int REQUESTS_PER_WINDOW = 60;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final CalendarFeedTokens tokens;
    private final PostService posts;
    private final Clock clock;
    private final PersonRateLimiter limiter;

    public CalendarFeedController(CalendarFeedTokens tokens, PostService posts, Clock clock) {
        this.tokens = tokens;
        this.posts = posts;
        this.clock = clock;
        this.limiter = new PersonRateLimiter(clock, REQUESTS_PER_WINDOW, WINDOW);
    }

    @SelfAuthenticated
    @Get("/{token}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<?> feed(@PathVariable String token) throws SQLException {
        CalendarFeedTokens.FeedAccess access;
        try {
            access = tokens.authenticateFeed(token);
        } catch (ProblemException refused) {
            throw new ProblemException(404, "Not Found", "The calendar feed does not exist.");
        }
        PersonRateLimiter.Decision decision = limiter.tryAcquire(access.tokenId().toString());
        if (!decision.allowed()) {
            return tooManyRequests(decision.retryAfterSeconds());
        }
        Instant now = clock.instant();
        String body = IcsFeed.render(now, posts.feedEvents(now, access.publisher()));
        return HttpResponse.ok(body).contentType(MediaType.of("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

    private static HttpResponse<?> tooManyRequests(long retryAfterSeconds) {
        ProblemDetails details = ProblemDetails.of(429, "Too Many Requests",
                "The request budget of this calendar feed is used up. Retry after the number of seconds in Retry-After.",
                "/calendar/feed");
        return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .contentType(MediaType.of(ProblemDetails.MEDIA_TYPE))
                .body(ProblemResponse.from(details));
    }
}
