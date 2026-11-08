package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The series catalogue: what an editor may store, what is refused, and what a viewer reads. */
class SeriesCatalogueTest {

    @Test
    void aSeriesWithAllFieldsIsStoredAndReturnedWithAnOpaqueIdentifier() throws SQLException {
        SeriesFixture world = new SeriesFixture();

        ContentSeries stored = world.service.create(null, SeriesFixture.draft("Kubernetes in 30 s", "#kubernetes30s"));

        assertNotNull(stored.id(), "the stored series has an identifier");
        assertEquals(1, world.series.list(null).size(), "the catalogue holds the one series");
        assertEquals("Kubernetes in 30 s", stored.name(), "the name is kept");
        assertEquals("#kubernetes30s", stored.hashtag(), "the hashtag is kept");
        assertEquals("Kurz erklärt:", stored.openingFormula(), "the opening formula is kept");
        assertEquals(Audience.DEVELOPERS, stored.audience(), "the audience is kept");
        assertEquals(1, stored.weekday(), "the weekday is kept");
        assertEquals(18, stored.postingSlot(), "the posting slot is kept");
        assertEquals(Map.of("de", "Kurz erklärt"), stored.badgeText(), "the badge text per language is kept");
        assertTrue(stored.active(), "a new series is active when the editor says so");
    }

    @Test
    void theHashtagIsStoredInLowerCaseSoThatTwoSpellingsCannotBeTwoSeries() throws SQLException {
        SeriesFixture world = new SeriesFixture();

        ContentSeries stored = world.service.create(null, SeriesFixture.draft("Live", "#LiveMigration"));

        assertEquals("#livemigration", stored.hashtag(), "the hashtag is stored in lower case");
    }

    @Test
    void aRawHexColourIsRefusedWithProblemDetailsAndNothingIsStored() {
        SeriesFixture world = new SeriesFixture();
        SeriesDraft draft = withColour(SeriesFixture.draft("Live", "#live"), "#6638e4");

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertEquals(400, refused.status(), "a hex value is a bad request");
        assertTrue(refused.detail().contains("token"), "the answer says a colour is a token reference");
        assertEquals(0, world.series.writes(), "nothing is stored after a refusal");
    }

    @Test
    void anUnknownAudienceIsRefusedNamingTheAllowedValues() {
        SeriesFixture world = new SeriesFixture();
        SeriesDraft draft = withAudience(SeriesFixture.draft("Live", "#live"), "everyone");

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertEquals(400, refused.status(), "an unknown audience is a bad request");
        assertTrue(refused.detail().contains("players, developers, mixed"), "the answer names the allowed values");
        assertEquals(0, world.series.writes(), "nothing is stored after a refusal");
    }

    @Test
    void aPostingSlotOtherThanTheHouseSlotsIsRefused() {
        SeriesFixture world = new SeriesFixture();
        SeriesDraft draft = withSlot(SeriesFixture.draft("Live", "#live"), 15);

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertEquals(400, refused.status(), "a slot outside 12, 18 and 21 is a bad request");
    }

    @Test
    void aSeriesWithoutBadgeTextInAnyLanguageIsRefused() {
        SeriesFixture world = new SeriesFixture();
        SeriesDraft draft = withBadge(SeriesFixture.draft("Live", "#live"), Map.of());

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertEquals(400, refused.status(), "a series needs a badge text");
    }

    @Test
    void aHashtagAlreadyUsedByAnotherSeriesIsRefusedAsAConflict() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("First", "#shared"));

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.create(null, SeriesFixture.draft("Second", "#shared")));

        assertEquals(409, refused.status(), "a second series with the same hashtag conflicts");
    }

    @Test
    void anEditorChangesTheNameAndTheSeriesKeepsItsIdentifierAndCreationTime() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries created = world.service.create(null, SeriesFixture.draft("Old name", "#survival"));
        world.clock.advance(java.time.Duration.ofDays(1));

        ContentSeries changed = world.service.update(null, created.id(), SeriesFixture.draft("New name", "#survival"));

        assertEquals(created.id(), changed.id(), "the identifier does not change");
        assertEquals(created.createdAt(), changed.createdAt(), "the creation time does not change");
        assertEquals("New name", world.service.find(null, created.id()).name(), "the change is stored");
    }

    @Test
    void anUpdateOfAMissingSeriesAnswersNotFound() {
        SeriesFixture world = new SeriesFixture();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.update(null, UUID.randomUUID(), SeriesFixture.draft("Any", "#any")));

        assertEquals(404, refused.status(), "a series that does not exist answers 404");
    }

    @Test
    void theCatalogueListsInactiveSeriesMarkedAsInactive() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("Active", "#active"));
        SeriesDraft inactive = withActive(SeriesFixture.draft("Paused", "#paused"), false);
        world.service.create(null, inactive);

        var listed = world.service.list(null);

        assertEquals(2, listed.size(), "inactive series are listed too");
        assertFalse(listed.stream().filter(s -> s.name().equals("Paused")).findFirst().orElseThrow().active(),
                "the inactive series is marked as inactive");
    }

    private static SeriesDraft withColour(SeriesDraft d, String colour) {
        return new SeriesDraft(d.name(), d.badgeText(), colour, d.badgeToken(), d.openingFormula(), d.layoutTemplate(),
                d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), d.active(), d.playlists());
    }

    private static SeriesDraft withAudience(SeriesDraft d, String audience) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), audience, d.weekday(), d.postingSlot(), d.active(), d.playlists());
    }

    private static SeriesDraft withSlot(SeriesDraft d, int slot) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), d.audience(), d.weekday(), slot, d.active(), d.playlists());
    }

    private static SeriesDraft withBadge(SeriesDraft d, Map<String, String> badge) {
        return new SeriesDraft(d.name(), badge, d.colourToken(), d.badgeToken(), d.openingFormula(), d.layoutTemplate(),
                d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), d.active(), d.playlists());
    }

    private static SeriesDraft withActive(SeriesDraft d, boolean active) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), active, d.playlists());
    }
}
