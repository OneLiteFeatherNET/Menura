package net.onelitefeather.menura.server.channel;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.channel.Actor;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConsentOutcome;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.security.CurrentPerson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;

/**
 * The redirect target of every platform consent. The platform sends the browser here with the state of the flow; the
 * server completes the consent and sends the browser back to the web app's channel page with the outcome. No token is
 * ever placed in the redirect.
 */
@Controller("/channel-connections")
public class ChannelConnectionController {

    private final ConnectionService connections;
    private final CurrentPerson currentPerson;
    private final IdentifierGenerator identifiers;
    private final String webAppUrl;

    public ChannelConnectionController(ConnectionService connections, CurrentPerson currentPerson,
                                       IdentifierGenerator identifiers,
                                       @Value("${menura.auth.web-app-url}") String webAppUrl) {
        this.connections = connections;
        this.currentPerson = currentPerson;
        this.identifiers = identifiers;
        this.webAppUrl = webAppUrl;
    }

    @Get("/callback")
    @RequiresRole({Role.PUBLISHER, Role.ADMIN})
    @ExecuteOn(TaskExecutors.BLOCKING)
    MutableHttpResponse<?> callback(HttpRequest<?> request, @QueryValue Optional<String> state,
                                    @QueryValue Optional<String> code, @QueryValue Optional<String> error) throws SQLException {
        Caller caller = currentPerson.require(request);
        Actor actor = new Actor(caller.personId(), caller.channel().value(), RequestIds.from(request, identifiers));
        ConsentOutcome outcome = connections.completeConsent(actor, state.orElse(null), code.orElse(null), error.orElse(null));
        StringBuilder location = new StringBuilder(webAppUrl.endsWith("/") ? webAppUrl : webAppUrl + "/")
                .append("channels?connection=").append(outcome.kind().name().toLowerCase(java.util.Locale.ROOT));
        if (outcome.channelId() != null) {
            location.append("&channel=").append(outcome.channelId());
        }
        if (outcome.reason() != null) {
            location.append("&reason=").append(URLEncoder.encode(outcome.reason(), StandardCharsets.UTF_8));
        }
        return HttpResponse.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
