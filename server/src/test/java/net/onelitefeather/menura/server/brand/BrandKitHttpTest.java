package net.onelitefeather.menura.server.brand;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import net.onelitefeather.menura.core.brand.BrandKit;
import net.onelitefeather.menura.core.brand.BrandKit.ColourToken;
import net.onelitefeather.menura.core.attribution.JoinService;
import net.onelitefeather.menura.core.attribution.support.InMemoryJoinStore;
import net.onelitefeather.menura.core.brand.InitialBrandKit;
import net.onelitefeather.menura.server.security.HeaderIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The brand kit over HTTP: who may read and who may publish, the problem responses and the exports. */
@MicronautTest(rebuildContext = true)
class BrandKitHttpTest {

    private static final String EDITOR = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String VIEWER = "2c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    InMemoryBrandKitRepository repository;

    @Inject
    InMemoryBrandKitAssets assets;

    @Inject
    InMemoryJoinStore joinStore;

    @Inject
    JoinService joins;

    @BeforeEach
    void startEmpty() {
        repository.clear();
        assets.clear();
        joinStore.clear();
    }

    @Test
    void aMissingLanguageNamesTheLanguagesThatExistAndSubstitutesNone() throws Exception {
        BrandKit base = InitialBrandKit.create();
        BrandKit withTexts = new BrandKit(base.colours(), base.ramps(), base.textRoles(), base.typography(), base.marks(),
                base.voice(), List.of(new BrandKit.LanguageText("de", "Danke", List.of("#menura")),
                new BrandKit.LanguageText("en", "Thanks", List.of())), base.templates());
        client.toBlocking().exchange(withPerson(publish(withTexts), EDITOR, "EDITOR"), String.class);

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(HttpRequest.GET("/brand-kit/texts/fr"), VIEWER, "VIEWER")));

        String detail = refused.getResponse().getBody(String.class).orElse("");
        assertEquals(404, refused.getStatus().getCode());
        assertTrue(detail.contains("[de, en]"), "the problem names the languages that exist: " + detail);
    }

    @Test
    void anonymousCallerIsRefusedWith401ProblemDetails() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/brand-kit")));

        assertEquals(401, refused.getStatus().getCode());
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
    }

    @Test
    void anySignedInRoleReadsTheCurrentKit() {
        HttpResponse<String> response = client.toBlocking().exchange(withPerson(HttpRequest.GET("/brand-kit"), VIEWER, "VIEWER"),
                String.class);

        assertEquals(200, response.code(), "reading the kit is open to every signed-in role");
        assertTrue(response.body().contains("\"primary\""), "the kit carries its colour tokens: " + response.body());
        assertEquals("no-store", response.header("Cache-Control"), "a kit read is never cached by an intermediary");
    }

    @Test
    void viewerIsRefusedWith403WhenPublishing() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(publish(InitialBrandKit.create()), VIEWER, "VIEWER")));

        assertEquals(403, refused.getStatus().getCode(), "publishing needs the editor role");
    }

    @Test
    void editorPublishesANewVersionAnswering201() throws Exception {
        HttpResponse<String> response = client.toBlocking().exchange(
                withPerson(publish(InitialBrandKit.create()), EDITOR, "EDITOR"), String.class);

        assertEquals(201, response.code(), "a publication answers 201 Created");
        assertTrue(response.body().contains("\"version\":2"), "the first publication after the seed is version 2: " + response.body());
    }

    @Test
    void aRefusedPublicationAnswers422NamingTheViolation() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(publish(surfaceWithoutDark()), EDITOR, "EDITOR")));

        assertEquals(422, refused.getStatus().getCode(), "a kit that breaks a rule is unprocessable");
        assertEquals("application/problem+json", refused.getResponse().getContentType().orElseThrow().toString());
        String detail = refused.getResponse().getBody(String.class).orElse("");
        assertTrue(detail.contains("colour token 'surface' lacks the dark role"), "the problem names the violation: " + detail);
    }

    @Test
    void theCssExportCarriesTheLightAndDarkValuesOfEachToken() {
        HttpResponse<String> response = client.toBlocking().exchange(withPerson(HttpRequest.GET("/brand-kit/css"), VIEWER, "VIEWER"),
                String.class);

        assertEquals(200, response.code());
        assertTrue(response.getContentType().orElseThrow().toString().startsWith("text/css"), "the export is CSS");
        assertTrue(response.body().contains("--brand-primary: #6638e4;"), "the light value is exported");
        assertTrue(response.body().contains("--brand-primary: #ccbdff;"), "the dark value is exported");
    }

    @Test
    void aLanguageWithoutATextIsNotFoundAndNoOtherLanguageIsSubstituted() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(HttpRequest.GET("/brand-kit/texts/fr"), VIEWER, "VIEWER")));

        assertEquals(404, refused.getStatus().getCode(), "the French end card is not substituted");
    }

    @Test
    void theShortTemplateIsReadableByFormatWithItsJoinDomainParameter() {
        HttpResponse<String> response = client.toBlocking().exchange(
                withPerson(HttpRequest.GET("/brand-kit/templates/short-9x16"), VIEWER, "VIEWER"), String.class);

        assertEquals(200, response.code());
        assertTrue(response.body().contains("\"join_domain\""), "the cta declares its join domain: " + response.body());
    }

    @Test
    void theRenderBundleCarriesTheInstallationJoinDomainForTheShortCallToAction() throws Exception {
        joins.saveSettings(UUID.fromString(EDITOR), "onelitefeather.net", null);

        HttpResponse<String> response = client.toBlocking().exchange(
                withPerson(HttpRequest.GET("/brand-kit/render-bundle"), VIEWER, "VIEWER"), String.class);

        assertEquals(200, response.code());
        assertTrue(response.body().contains("\"joinDomain\":\"onelitefeather.net\""),
                "the bundle names the installation join domain for the cta: " + response.body());
    }

    @Test
    void theRenderBundleCarriesNoJoinDomainWhileNoneIsConfigured() {
        HttpResponse<String> response = client.toBlocking().exchange(
                withPerson(HttpRequest.GET("/brand-kit/render-bundle"), VIEWER, "VIEWER"), String.class);

        assertEquals(200, response.code(), "a missing join domain is not an error");
        assertFalse(response.body().contains("\"joinDomain\":\""), "no join domain is carried: " + response.body());
    }

    @Test
    void aViewerMayNotPinAConsumerButAnEditorMay() {
        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(withPerson(pin("overlay-scene", 1), VIEWER, "VIEWER")));
        assertEquals(403, refused.getStatus().getCode(), "pinning needs the editor role");

        HttpResponse<String> pinned = client.toBlocking().exchange(withPerson(pin("overlay-scene", 1), EDITOR, "EDITOR"),
                String.class);
        assertEquals(200, pinned.code(), "the editor pins the overlay to version 1");
    }

    @Test
    void aPinnedRenderBundleIsTheVersionThePinNames() {
        client.toBlocking().exchange(withPerson(pin("renderer", 1), EDITOR, "EDITOR"), String.class);
        client.toBlocking().exchange(withPerson(publish(InitialBrandKit.create()), EDITOR, "EDITOR"), String.class);

        HttpResponse<String> bundle = client.toBlocking().exchange(
                withPerson(HttpRequest.GET("/brand-kit/render-bundle?consumer=renderer"), VIEWER, "VIEWER"), String.class);

        assertTrue(bundle.body().contains("\"version\":1"), "the renderer receives its pinned version: " + bundle.body());
    }

    private static MutableHttpRequest<?> publish(BrandKit kit) {
        try {
            return HttpRequest.POST("/brand-kit/versions", new ObjectMapper().writeValueAsString(kit))
                    .contentType(MediaType.APPLICATION_JSON);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static MutableHttpRequest<?> pin(String consumer, int version) {
        return HttpRequest.PUT("/brand-kit/pins/" + consumer, "{\"version\":" + version + "}")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static BrandKit surfaceWithoutDark() {
        BrandKit base = InitialBrandKit.create();
        List<ColourToken> colours = new ArrayList<>(base.colours());
        colours.replaceAll(token -> token.name().equals("surface") ? new ColourToken("surface", "#fffbff", null) : token);
        return new BrandKit(colours, base.ramps(), base.textRoles(), base.typography(), base.marks(), base.voice(),
                base.texts(), base.templates());
    }

    private static MutableHttpRequest<?> withPerson(MutableHttpRequest<?> request, String person, String roles) {
        return request.header(HeaderIdentityFilter.PERSON_HEADER, person).header(HeaderIdentityFilter.ROLES_HEADER, roles);
    }
}
