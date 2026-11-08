package net.onelitefeather.menura.server.secret;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Post;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.security.CurrentPerson;

import java.sql.SQLException;
import java.util.Set;

/** The signed-in person's own calendar feed token: issue (rotating any previous one) and revoke. */
@Controller("/calendar-feed-token")
public class CalendarFeedTokenController {

    private final CurrentPerson currentPerson;
    private final CalendarFeedTokens tokens;
    private final IdentifierGenerator identifiers;

    public CalendarFeedTokenController(CurrentPerson currentPerson, CalendarFeedTokens tokens,
                                       IdentifierGenerator identifiers) {
        this.currentPerson = currentPerson;
        this.tokens = tokens;
        this.identifiers = identifiers;
    }

    @Post
    public HttpResponse<IssuedCalendarFeedToken> issue(HttpRequest<?> request) throws SQLException {
        boolean publisher = net.onelitefeather.menura.server.auth.CurrentPerson.of(request)
                .map(person -> person.hasAnyRole(Set.of(Role.PUBLISHER))).orElse(false);
        String token = tokens.issue(currentPerson.require(request), RequestIds.from(request, identifiers), publisher);
        return HttpResponse.created(new IssuedCalendarFeedToken(token))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Delete
    public HttpResponse<Void> revoke(HttpRequest<?> request) throws SQLException {
        tokens.revoke(currentPerson.require(request), RequestIds.from(request, identifiers));
        return HttpResponse.noContent();
    }
}
