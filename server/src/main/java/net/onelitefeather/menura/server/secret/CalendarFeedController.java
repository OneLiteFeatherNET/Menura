package net.onelitefeather.menura.server.secret;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import net.onelitefeather.menura.server.security.SelfAuthenticated;

import java.sql.SQLException;

/**
 * The calendar feed a calendar client subscribes to. The token in the path is the credential, so the route is
 * {@link SelfAuthenticated}. The feed itself is a placeholder until the iCalendar change lands.
 */
@Controller("/calendar/feed")
public class CalendarFeedController {

    static final String PLACEHOLDER_FEED = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//OneLiteFeather//Menura//EN\r\nEND:VCALENDAR\r\n";

    private final CalendarFeedTokens tokens;

    public CalendarFeedController(CalendarFeedTokens tokens) {
        this.tokens = tokens;
    }

    @SelfAuthenticated
    @Get("/{token}")
    public HttpResponse<String> feed(@PathVariable String token) throws SQLException {
        tokens.authenticate(token);
        return HttpResponse.ok(PLACEHOLDER_FEED).contentType(MediaType.of("text/calendar"));
    }
}
