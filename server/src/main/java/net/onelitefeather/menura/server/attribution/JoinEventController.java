package net.onelitefeather.menura.server.attribution;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import net.onelitefeather.menura.core.attribution.JoinAnswer;
import net.onelitefeather.menura.core.attribution.JoinEvent;
import net.onelitefeather.menura.core.attribution.JoinPayloads;
import net.onelitefeather.menura.core.attribution.JoinService;
import net.onelitefeather.menura.core.attribution.StatedSource;
import net.onelitefeather.menura.server.attribution.JoinViews.AnswerReceipt;
import net.onelitefeather.menura.server.attribution.JoinViews.EventReceipt;
import net.onelitefeather.menura.server.security.SelfAuthenticated;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * The join events and source answers of the network proxy (change add-join-attribution). The proxy is a machine client,
 * so the routes are {@link SelfAuthenticated}: {@link JoinProxyGuard} checks its credential and budget before any body is
 * read into the service. A repeated delivery answers 200 and changes nothing; a new one answers 201.
 */
@Controller("/join-events")
public class JoinEventController {

    private final JoinService joins;
    private final JoinProxyGuard guard;

    public JoinEventController(JoinService joins, JoinProxyGuard guard) {
        this.joins = joins;
        this.guard = guard;
    }

    @Post
    @SelfAuthenticated
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<?> record(@Body @Nullable Map<String, Object> body, HttpRequest<?> request) throws SQLException {
        MutableHttpResponse<?> refused = guard.refusalFor(request);
        if (refused != null) {
            return refused;
        }
        JoinService.Recorded<JoinEvent> recorded = joins.recordJoin(JoinPayloads.event(body));
        EventReceipt receipt = EventReceipt.of(recorded.value());
        return recorded.created() ? HttpResponse.created(receipt) : HttpResponse.ok(receipt);
    }

    @Post("/{eventId}/source-answer")
    @SelfAuthenticated
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<?> answer(@PathVariable UUID eventId, @Body @Nullable Map<String, Object> body, HttpRequest<?> request)
            throws SQLException {
        MutableHttpResponse<?> refused = guard.refusalFor(request);
        if (refused != null) {
            return refused;
        }
        StatedSource source = JoinPayloads.answer(body);
        JoinService.Recorded<JoinAnswer> recorded = joins.recordAnswer(eventId, source);
        AnswerReceipt receipt = AnswerReceipt.of(recorded.value());
        return recorded.created() ? HttpResponse.created(receipt) : HttpResponse.ok(receipt);
    }
}
