package net.onelitefeather.menura.server.channel;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.core.annotation.Nullable;
import net.onelitefeather.menura.core.channel.Actor;
import net.onelitefeather.menura.core.channel.ChannelSnapshot;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.publish.ChannelQuotaService;
import net.onelitefeather.menura.core.paging.Cursor;
import net.onelitefeather.menura.core.paging.PageRequest;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.RequiresRole;
import net.onelitefeather.menura.server.channel.ChannelViews.Authorization;
import net.onelitefeather.menura.server.channel.ChannelViews.ChannelPage;
import net.onelitefeather.menura.server.channel.ChannelViews.ChannelView;
import net.onelitefeather.menura.server.channel.ChannelViews.ConnectRequest;
import net.onelitefeather.menura.server.channel.ChannelViews.ConsentRequest;
import net.onelitefeather.menura.server.channel.ChannelViews.Disconnection;
import net.onelitefeather.menura.server.security.Caller;
import net.onelitefeather.menura.server.security.CurrentPerson;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Channels of the hub: list and read them (any signed-in person), start a connection or a reconnection through the
 * platform's consent, and disconnect (publishers and administrators). No response carries a token.
 */
@Controller("/channels")
public class ChannelController {

    static final Pattern PLATFORM = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
    static final Pattern FEATURE = Pattern.compile("[a-z][a-z0-9_]{0,39}");
    static final int MAX_FEATURES = 10;

    private final ConnectionService connections;
    private final CurrentPerson currentPerson;
    private final IdentifierGenerator identifiers;
    private final ChannelQuotaService quota;

    public ChannelController(ConnectionService connections, CurrentPerson currentPerson, IdentifierGenerator identifiers,
                             ChannelQuotaService quota) {
        this.connections = connections;
        this.currentPerson = currentPerson;
        this.identifiers = identifiers;
        this.quota = quota;
    }

    @Get
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    ChannelPage list(@QueryValue Optional<Integer> limit, @QueryValue Optional<String> cursor) throws SQLException {
        PageRequest page = PageRequest.of(limit.orElse(null), cursor.orElse(null));
        UUID after = page.afterKey() == null ? null : parseId(page.afterKey());
        List<ChannelSnapshot> rows = connections.list(after, page.limit() + 1);
        boolean more = rows.size() > page.limit();
        List<ChannelSnapshot> items = more ? rows.subList(0, page.limit()) : rows;
        String next = more ? Cursor.encode(items.get(items.size() - 1).channel().id().toString()) : null;
        return new ChannelPage(items.stream().map(ChannelViews::of).toList(), next);
    }

    @Get("/{id}")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    ChannelView get(@PathVariable UUID id) throws SQLException {
        return ChannelViews.of(connections.snapshot(id));
    }

    /**
     * The remaining daily quota of a YouTube channel: the units and the uploads of the current Pacific day that are still
     * left, and when they start over (change add-youtube-integration). Any signed-in person may read it.
     */
    @Get("/{id}/quota")
    @RequiresRole
    @ExecuteOn(TaskExecutors.BLOCKING)
    ChannelQuotaView quota(@PathVariable UUID id) throws SQLException {
        return ChannelQuotaView.of(quota.quota(id));
    }

    /** Starts a connection of a new channel. The browser is sent to the platform's consent with the returned URL. */
    @Post("/connections")
    @RequiresRole({Role.PUBLISHER, Role.ADMIN})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<Authorization> connect(HttpRequest<?> request, @Body ConnectRequest body) throws SQLException {
        if (body == null || body.platform() == null || !PLATFORM.matcher(body.platform()).matches()) {
            throw new ProblemException(400, "Bad Request", "The platform identifier is not valid.");
        }
        Actor actor = actorOf(request);
        return authorization(connections.startConsent(actor, body.platform(), features(body.features()), null));
    }

    /** Starts a reconnection of a channel, or a consent that enables further features on it. */
    @Post("/{id}/consent")
    @RequiresRole({Role.PUBLISHER, Role.ADMIN})
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<Authorization> consent(HttpRequest<?> request, @PathVariable UUID id,
                                        @Nullable @Body ConsentRequest body) throws SQLException {
        String platform = connections.snapshot(id).channel().platform();
        Actor actor = actorOf(request);
        return authorization(connections.startConsent(actor, platform, features(body == null ? null : body.features()), id));
    }

    /** Disconnects a channel: the platform grant is revoked where possible, the stored tokens are always deleted. */
    @Delete("/{id}")
    @RequiresRole({Role.PUBLISHER, Role.ADMIN})
    @ExecuteOn(TaskExecutors.BLOCKING)
    Disconnection disconnect(HttpRequest<?> request, @PathVariable UUID id) throws SQLException {
        return new Disconnection(connections.disconnect(actorOf(request), id));
    }

    private HttpResponse<Authorization> authorization(java.net.URI uri) {
        return HttpResponse.accepted()
                .body(new Authorization(uri.toString()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private Actor actorOf(HttpRequest<?> request) {
        Caller caller = currentPerson.require(request);
        return new Actor(caller.personId(), caller.channel().value(), RequestIds.from(request, identifiers));
    }

    static Set<String> features(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        if (values.size() > MAX_FEATURES) {
            throw new ProblemException(400, "Bad Request", "At most " + MAX_FEATURES + " features can be requested.");
        }
        for (String value : values) {
            if (value == null || !FEATURE.matcher(value).matches()) {
                throw new ProblemException(400, "Bad Request", "A feature name is not valid.");
            }
        }
        return Set.copyOf(values);
    }

    private static UUID parseId(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException notAnId) {
            throw new ProblemException(400, "Invalid cursor", "The cursor is not valid for this collection.");
        }
    }
}
