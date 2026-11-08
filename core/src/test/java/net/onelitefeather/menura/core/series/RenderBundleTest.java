package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.SeriesReports.RenderBundle;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The render bundle the external renderer reads: the active series and one call to action, checked against a bundle fixture. */
class RenderBundleTest {

    @Test
    void theBundleContainsExactlyTheActiveSeriesWithTheRendererFields() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        ContentSeries first = world.service.create(null, SeriesFixture.draft("Erste", "#erste"));
        ContentSeries second = world.service.create(null, SeriesFixture.draft("Zweite", "#zweite"));
        world.service.create(null, withActive(SeriesFixture.draft("Pausiert", "#pausiert"), false));

        RenderBundle bundle = world.service.renderBundle(null, channel);

        assertEquals(2, bundle.series().size(), "the inactive series is not in the bundle");
        assertTrue(bundle.series().stream().anyMatch(s -> s.id().equals(first.id())), "the first active series is in it");
        var entry = bundle.series().stream().filter(s -> s.id().equals(second.id())).findFirst().orElseThrow();
        assertEquals("primary", entry.colourToken(), "the bundle carries the colour token, not a raw value");
        assertEquals("Kurz erklärt:", entry.openingFormula(), "the bundle carries the opening formula");
        assertEquals("#zweite", entry.hashtag(), "the bundle carries the hashtag");
        assertEquals("Kurz erklärt", entry.badgeText().get("de"), "the bundle carries the badge text per language");
    }

    @Test
    void aJoinDomainGivesOneCallToActionThatNamesIt() throws SQLException {
        SeriesFixture world = new SeriesFixture(() -> Optional.of("onelitefeather.net"));
        UUID channel = world.channel("UC1");
        world.service.create(null, SeriesFixture.draft("Erste", "#erste"));

        RenderBundle bundle = world.service.renderBundle(null, channel);

        assertTrue(bundle.callToAction().text().contains("onelitefeather.net"), "the one call to action names the domain");
    }

    @Test
    void withoutAJoinDomainTheBundleHasNoCallToActionAndNoError() throws SQLException {
        SeriesFixture world = new SeriesFixture(() -> Optional.empty());
        UUID channel = world.channel("UC1");
        world.service.create(null, SeriesFixture.draft("Erste", "#erste"));

        RenderBundle bundle = world.service.renderBundle(null, channel);

        assertNull(bundle.callToAction(), "no call to action without a join domain");
        assertEquals(1, bundle.series().size(), "the series are still delivered");
    }

    @Test
    void aBlankJoinDomainIsTreatedAsNotConfigured() throws SQLException {
        SeriesFixture world = new SeriesFixture(() -> Optional.of("  "));
        UUID channel = world.channel("UC1");

        RenderBundle bundle = world.service.renderBundle(null, channel);

        assertNull(bundle.callToAction(), "a blank domain produces no call to action");
    }

    @Test
    void aBundleForAnUnknownChannelAnswersNotFound() {
        SeriesFixture world = new SeriesFixture();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.renderBundle(null, UUID.randomUUID()));

        assertEquals(404, refused.status(), "an unknown channel answers 404");
    }

    @Test
    void anInactiveSeriesIsNotInTheBundleOfAnyChannel() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        world.service.create(null, withActive(SeriesFixture.draft("Pausiert", "#pausiert"), false));

        RenderBundle bundle = world.service.renderBundle(null, channel);

        assertFalse(bundle.series().stream().anyMatch(s -> s.hashtag().equals("#pausiert")), "no inactive series");
    }

    private static SeriesDraft withActive(SeriesDraft d, boolean active) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), active, d.playlists());
    }
}
