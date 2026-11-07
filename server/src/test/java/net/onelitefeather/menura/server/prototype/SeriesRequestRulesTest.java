package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input limits of series creation and post assignment. Each test names one rule; a refusal is a 422 whose detail
 * names the field, and never echoes the raw input. Each test builds its own store on a fixed clock.
 */
class SeriesRequestRulesTest {

    private static final String LONG_80 = "x".repeat(80);
    private static final String LONG_81 = "x".repeat(81);

    /** A valid request that a test changes in one place. */
    private static final class Form {

        String name = "Neu";
        Model.Localized badge = new Model.Localized("Neu", "New");
        String colorToken = "series-k8s";
        String badgeTextColor = "#FFFFFF";
        String openingFormula = "Kurz:";
        String hashtag = "#neu";
        String audience = "developers";
        String weekday = "THURSDAY";
        String slot = "18:00";
        Map<String, String> playlists = Map.of();
        Boolean active = null;
        String color = null;

        Model.SeriesRequest build() {
            return new Model.SeriesRequest(name, badge, colorToken, badgeTextColor, openingFormula, hashtag, audience,
                    weekday, slot, playlists, active, color);
        }
    }

    private static DemoStore store() {
        return new DemoStore(Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));
    }

    private static ProblemException refusedBy(Consumer<Form> change) {
        Form form = new Form();
        change.accept(form);
        return assertThrows(ProblemException.class, () -> store().createSeries(form.build()));
    }

    private static void assertRefusedNaming(String field, Consumer<Form> change) {
        ProblemException problem = refusedBy(change);

        assertEquals(422, problem.status());
        assertTrue(problem.detail().contains(field), "the detail names the field: " + problem.detail());
    }

    // Length limits

    @Test
    void aNameOfEightyCharactersIsAccepted() {
        assertDoesNotThrow(() -> create(form -> form.name = LONG_80));
    }

    @Test
    void aNameLongerThanEightyCharactersIsRefused() {
        assertRefusedNaming("name", form -> form.name = LONG_81);
    }

    @Test
    void aBadgeTextLongerThanEightyCharactersIsRefused() {
        assertRefusedNaming("badge", form -> form.badge = new Model.Localized(LONG_81, "New"));
    }

    @Test
    void aBadgeTextOfEightyCharactersIsAccepted() {
        assertDoesNotThrow(() -> create(form -> form.badge = new Model.Localized("Neu", LONG_80)));
    }

    @Test
    void anOpeningFormulaLongerThanTwoHundredCharactersIsRefused() {
        assertRefusedNaming("openingFormula", form -> form.openingFormula = "x".repeat(201));
    }

    @Test
    void anOpeningFormulaOfTwoHundredCharactersIsAccepted() {
        assertDoesNotThrow(() -> create(form -> form.openingFormula = "x".repeat(200)));
    }

    // Hashtag

    @Test
    void aHashtagWithUppercaseLettersIsRefused() {
        assertRefusedNaming("hashtag", form -> form.hashtag = "#K8S");
    }

    @Test
    void aHashtagWithoutTheLeadingHashIsRefused() {
        assertRefusedNaming("hashtag", form -> form.hashtag = "neu");
    }

    @Test
    void aHashtagWithAnotherCharacterIsRefused() {
        assertRefusedNaming("hashtag", form -> form.hashtag = "#neu-x");
    }

    @Test
    void aHashtagOfOneCharacterAfterTheHashIsRefused() {
        assertRefusedNaming("hashtag", form -> form.hashtag = "#a");
    }

    @Test
    void aHashtagOfThirtyOneCharactersAfterTheHashIsRefused() {
        assertRefusedNaming("hashtag", form -> form.hashtag = "#" + "a".repeat(31));
    }

    @Test
    void aHashtagOfThirtyCharactersAfterTheHashIsAccepted() {
        assertDoesNotThrow(() -> create(form -> form.hashtag = "#" + "a".repeat(30)));
    }

    @Test
    void theHashtagRefusalDoesNotEchoTheValue() {
        ProblemException problem = refusedBy(form -> form.hashtag = "#<script>alert(1)</script>");

        assertFalse(problem.detail().contains("script"), problem.detail());
        assertTrue(problem.detail().contains("2–30"), problem.detail());
    }

    // Colours

    @Test
    void aBadgeTextColourThatIsNotSixDigitHexIsRefused() {
        assertRefusedNaming("badgeTextColor", form -> form.badgeTextColor = "#FFF");
    }

    @Test
    void aColourFieldThatIsNotSixDigitHexIsRefusedEvenThoughItIsIgnored() {
        assertRefusedNaming("color", form -> form.color = "red");
    }

    @Test
    void aColourFieldInSixDigitHexIsAccepted() {
        assertDoesNotThrow(() -> create(form -> form.color = "#2f6feb"));
    }

    @Test
    void aBadgeTextColourRefusalDoesNotEchoTheValue() {
        ProblemException problem = refusedBy(form -> form.badgeTextColor = "evil-value");

        assertFalse(problem.detail().contains("evil-value"), problem.detail());
    }

    // Playlists

    @Test
    void morePlaylistEntriesThanTenAreRefused() {
        Map<String, String> playlists = new HashMap<>();
        for (int number = 1; number <= 11; number++) {
            playlists.put("kanal-" + number, "https://example.org/" + number);
        }

        assertRefusedNaming("playlists", form -> form.playlists = playlists);
    }

    @Test
    void aPlaylistKeyThatIsNoKnownChannelIsRefusedWithoutEchoingIt() {
        ProblemException problem = refusedBy(form -> form.playlists = Map.of("geheim-kanal",
                "https://example.org/list"));

        assertEquals(422, problem.status());
        assertTrue(problem.detail().startsWith("playlists:"), problem.detail());
        assertFalse(problem.detail().contains("geheim-kanal"), problem.detail());
    }

    @Test
    void aPlaylistValueThatIsNotHttpsIsRefused() {
        assertRefusedNaming("playlists", form -> form.playlists = Map.of("yt-de", "http://example.org/list"));
    }

    @Test
    void aPlaylistValueLongerThanThreeHundredCharactersIsRefused() {
        String url = "https://example.org/" + "a".repeat(290);

        assertRefusedNaming("playlists", form -> form.playlists = Map.of("yt-de", url));
    }

    @Test
    void aPlaylistValueOfThreeHundredCharactersIsAccepted() {
        String url = "https://example.org/" + "a".repeat(300 - "https://example.org/".length());
        assertEquals(300, url.length());

        assertDoesNotThrow(() -> create(form -> form.playlists = Map.of("yt-de", url)));
    }

    @Test
    void aNullPlaylistKeyIsRefusedWith422() {
        Map<String, String> playlists = new HashMap<>();
        playlists.put(null, "https://example.org/list");

        assertRefusedNaming("playlists", form -> form.playlists = playlists);
    }

    @Test
    void aNullPlaylistValueIsRefusedWith422() {
        Map<String, String> playlists = new HashMap<>();
        playlists.put("yt-de", null);

        assertRefusedNaming("playlists", form -> form.playlists = playlists);
    }

    @Test
    void aMissingPlaylistMapIsAcceptedAsEmpty() {
        assertDoesNotThrow(() -> create(form -> form.playlists = null));
    }

    // Required fields

    @Test
    void aMissingAudienceIsRefusedWith422NotAServerError() {
        assertRefusedNaming("audience", form -> form.audience = null);
    }

    @Test
    void aMissingNameIsRefusedWith422() {
        assertRefusedNaming("name", form -> form.name = null);
    }

    @Test
    void aMissingHashtagIsRefusedWith422() {
        assertRefusedNaming("hashtag", form -> form.hashtag = null);
    }

    @Test
    void aMissingColourTokenIsRefusedWith422() {
        assertRefusedNaming("colorToken", form -> form.colorToken = null);
    }

    @Test
    void aMissingBadgeIsRefusedWith422() {
        assertRefusedNaming("badge", form -> form.badge = null);
    }

    @Test
    void aBadgeWithoutTheGermanTextIsRefusedWith422() {
        assertRefusedNaming("badge", form -> form.badge = new Model.Localized(null, "New"));
    }

    @Test
    void aBadgeWithoutTheEnglishTextIsRefusedWith422() {
        assertRefusedNaming("badge", form -> form.badge = new Model.Localized("Neu", null));
    }

    // Unknown values

    @Test
    void anUnknownColourTokenIsRefusedWithoutEchoingIt() {
        ProblemException problem = refusedBy(form -> form.colorToken = "series-geheim");

        assertEquals(422, problem.status());
        assertTrue(problem.detail().startsWith("colorToken:"), problem.detail());
        assertFalse(problem.detail().contains("series-geheim"), problem.detail());
    }

    @Test
    void anUnknownAudienceIsRefusedWithoutEchoingIt() {
        ProblemException problem = refusedBy(form -> form.audience = "geheim");

        assertTrue(problem.detail().startsWith("audience:"), problem.detail());
        assertFalse(problem.detail().contains("geheim"), problem.detail());
    }

    @Test
    void assigningAnUnknownSeriesSaysUnbekannteSerieWithoutTheId() {
        ProblemException problem = assertThrows(ProblemException.class,
                () -> store().assign("p-55", new Model.AssignRequest("geheime-serie", false)));

        assertEquals(422, problem.status());
        assertEquals("unbekannte Serie.", problem.detail());
    }

    // Count

    @Test
    void theFiftiethSeriesIsAcceptedAndTheFiftyFirstIsRefused() {
        DemoStore store = store();
        for (int number = 5; number <= 50; number++) {
            Form form = new Form();
            form.name = "Serie " + number;
            form.hashtag = "#serie" + number;
            store.createSeries(form.build());
        }
        Form extra = new Form();
        extra.name = "Zu viel";
        extra.hashtag = "#zuviel";

        ProblemException problem = assertThrows(ProblemException.class, () -> store.createSeries(extra.build()));

        assertEquals(422, problem.status());
        assertTrue(problem.detail().contains("50"), problem.detail());
        assertEquals(50, store.series().size());
    }

    private static void create(Consumer<Form> change) {
        Form form = new Form();
        change.accept(form);
        store().createSeries(form.build());
    }
}
