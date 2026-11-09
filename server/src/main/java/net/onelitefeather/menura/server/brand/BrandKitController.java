package net.onelitefeather.menura.server.brand;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.sse.Event;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.core.brand.BrandKitCss;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.brand.BrandKit.LanguageText;
import net.onelitefeather.menura.core.brand.BrandKit.Template;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.core.series.JoinDomain;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.server.audit.RequestIds;
import net.onelitefeather.menura.server.auth.CurrentPerson;
import net.onelitefeather.menura.server.auth.RequiresRole;
import org.reactivestreams.Publisher;

import java.sql.SQLException;

/**
 * The central brand kit over REST. Every signed-in role reads it: the newest version, a named version, or the version a
 * consumer has pinned. Only the editor role publishes versions and sets pins. The overlay, the desktop app and the OBS
 * plugin read it here; no route depends on MCP.
 */
@Controller("/brand-kit")
public class BrandKitController {

    private final BrandKits kits;
    private final IdentifierGenerator identifiers;
    private final BrandKitNotifications notifications;
    private final JoinDomain joinDomain;

    public BrandKitController(BrandKits kits, IdentifierGenerator identifiers, BrandKitNotifications notifications,
                              JoinDomain joinDomain) {
        this.notifications = notifications;
        this.kits = kits;
        this.identifiers = identifiers;
        this.joinDomain = joinDomain;
    }

    @RequiresRole
    @Get
    public HttpResponse<BrandKitViews.Version> current(HttpRequest<?> request) throws SQLException {
        return read(kits.current());
    }

    @RequiresRole
    @Get("/versions/{version}")
    public HttpResponse<BrandKitViews.Version> version(@PathVariable String version, HttpRequest<?> request) throws SQLException {
        return read(kits.resolve(number(version), null));
    }

    @RequiresRole(Role.EDITOR)
    @Post("/versions")
    public HttpResponse<BrandKitViews.Version> publish(@Body BrandKit body, HttpRequest<?> request) throws SQLException {
        StoredKitVersion published = kits.publish(person(request), body, RequestIds.from(request, identifiers));
        return HttpResponse.created(BrandKitViews.Version.of(published)).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** The JSON render bundle an external renderer reads: the version, its whole kit and the installation join domain. */
    @RequiresRole
    @Get("/render-bundle")
    public HttpResponse<BrandKitViews.RenderBundle> renderBundle(@Nullable @QueryValue Integer version,
                                                                 @Nullable @QueryValue String consumer, HttpRequest<?> request)
            throws SQLException {
        return HttpResponse.ok(BrandKitViews.RenderBundle.of(kits.resolve(version, consumer), joinDomain.joinDomain()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** The colour tokens as CSS custom properties, light and dark. */
    @RequiresRole
    @Get("/css")
    public HttpResponse<String> css(@Nullable @QueryValue Integer version, @Nullable @QueryValue String consumer,
                                    HttpRequest<?> request) throws SQLException {
        String css = BrandKitCss.of(kits.resolve(version, consumer).kit());
        return HttpResponse.ok(css).contentType(MediaType.of("text/css")).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** The layout template of one format, with its declared parameters. */
    @RequiresRole
    @Get("/templates/{format}")
    public HttpResponse<Template> template(@PathVariable String format, @Nullable @QueryValue Integer version,
                                           @Nullable @QueryValue String consumer, HttpRequest<?> request) throws SQLException {
        StoredKitVersion stored = kits.resolve(version, consumer);
        Template template = stored.kit().template(format).orElseThrow(() ->
                new ProblemException(404, "Not Found", "This brand kit version has no template for this format."));
        return HttpResponse.ok(template).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** The end-card text and hashtags of one language. A language without an entry is 404; none is substituted. */
    @RequiresRole
    @Get("/texts/{language}")
    public HttpResponse<LanguageText> text(@PathVariable String language, @Nullable @QueryValue Integer version,
                                           @Nullable @QueryValue String consumer, HttpRequest<?> request) throws SQLException {
        StoredKitVersion stored = kits.resolve(version, consumer);
        LanguageText text = stored.kit().text(language).orElseThrow(() -> new ProblemException(404, "Not Found",
                "This brand kit version has no text for the language '" + language + "'. Available languages: "
                        + stored.kit().texts().stream().map(LanguageText::language).sorted().toList()
                        + ". No other language is substituted."));
        return HttpResponse.ok(text).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    /** The version notifications as Server-Sent Events. Every signed-in role may subscribe; a reconnect reads the version. */
    @RequiresRole
    @Get("/events")
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<String>> events(HttpRequest<?> request) {
        person(request);
        return notifications.stream();
    }

    @RequiresRole
    @Get("/pins/{consumer}")
    public HttpResponse<BrandKitViews.Pin> pin(@PathVariable String consumer, HttpRequest<?> request) throws SQLException {
        return HttpResponse.ok(BrandKitViews.Pin.of(kits.pinOf(consumer)));
    }

    @RequiresRole(Role.EDITOR)
    @Put("/pins/{consumer}")
    public HttpResponse<BrandKitViews.Pin> setPin(@PathVariable String consumer, @Body BrandKitViews.PinRequest body,
                                                  HttpRequest<?> request) throws SQLException {
        if (body == null || body.version() == null) {
            throw new ProblemException(400, "Invalid pin", "The pin names the version to pin in its version member.");
        }
        KitPin pin = kits.pin(person(request), consumer, body.version(), RequestIds.from(request, identifiers));
        return HttpResponse.ok(BrandKitViews.Pin.of(pin));
    }

    @RequiresRole(Role.EDITOR)
    @Delete("/pins/{consumer}")
    public HttpResponse<Void> unpin(@PathVariable String consumer, HttpRequest<?> request) throws SQLException {
        kits.unpin(person(request), consumer, RequestIds.from(request, identifiers));
        return HttpResponse.noContent();
    }

    private static HttpResponse<BrandKitViews.Version> read(StoredKitVersion stored) {
        return HttpResponse.ok(BrandKitViews.Version.of(stored)).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private static CurrentPerson person(HttpRequest<?> request) {
        return CurrentPerson.of(request).orElseThrow(() -> new ProblemException(401, "Unauthorized", "Sign in to continue."));
    }

    /** A version number that is not a number names no version, so it answers 404 like an unknown one. */
    private static Integer number(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException notANumber) {
            throw new ProblemException(404, "Not Found", "No brand kit version with this number.");
        }
    }
}
