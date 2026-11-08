package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The badge contrast rule: the text token must reach 4.5:1 against the series colour token, in both roles. */
class BadgeContrastTest {

    @Test
    void aPairAtTheThresholdIsAccepted() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        // edge (#767676) on on-primary (#ffffff) is 4.54:1, above the 4.5:1 threshold.
        SeriesDraft draft = withColour(SeriesFixture.draft("Edge", "#edge"), "edge");

        ContentSeries stored = world.service.create(null, draft);

        assertEquals("edge", stored.colourToken(), "a pair that reaches 4.5:1 is stored");
    }

    @Test
    void aPairBelowTheThresholdIsRefusedNamingBothTokensAndTheRatio() {
        SeriesFixture world = new SeriesFixture();
        // pale (#959595) on on-primary (#ffffff) is 2.99:1.
        SeriesDraft draft = withColour(SeriesFixture.draft("Pale", "#pale"), "pale");

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertEquals(400, refused.status(), "a pair below the threshold is a bad request");
        assertTrue(refused.detail().contains("on-primary"), "the answer names the badge text token");
        assertTrue(refused.detail().contains("pale"), "the answer names the series colour token");
        assertTrue(refused.detail().contains("2.99:1"), "the answer names the measured ratio");
        assertEquals(0, world.series.writes(), "nothing is stored after a refusal");
    }

    @Test
    void aRatioJustBelowTheThresholdIsRoundedDownSoItIsNeverShownAsPassing() {
        SeriesFixture world = new SeriesFixture();
        // near (#777777) on on-primary (#ffffff) is 4.478:1, which must be shown as 4.47, not 4.48 or 4.5.
        SeriesDraft draft = withColour(SeriesFixture.draft("Near", "#near"), "near");

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertTrue(refused.detail().contains("4.47:1"), "the ratio is rounded down: " + refused.detail());
    }

    @Test
    void aColourChangeThatBreaksThePairIsRefusedAndTheStoredColourStaysUnchanged() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries stored = world.service.create(null, SeriesFixture.draft("Live", "#live"));
        SeriesDraft broken = withColour(SeriesFixture.draft("Live", "#live"), "pale");

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.update(null, stored.id(), broken));

        assertEquals(400, refused.status(), "the change is refused");
        assertEquals("primary", world.service.find(null, stored.id()).colourToken(),
                "the stored colour token is unchanged");
    }

    @Test
    void aTokenTheBrandKitDoesNotDefineIsRefusedNamingIt() {
        SeriesFixture world = new SeriesFixture();
        SeriesDraft draft = withColour(SeriesFixture.draft("Unknown", "#unknown"), "not-in-kit");

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertTrue(refused.detail().contains("not-in-kit"), "the answer names the missing token");
    }

    @Test
    void aLayoutTemplateTheBrandKitDoesNotHaveIsRefused() {
        SeriesFixture world = new SeriesFixture();
        SeriesDraft draft = withTemplate(SeriesFixture.draft("Grid", "#grid"), "cover-grid");

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.create(null, draft));

        assertTrue(refused.detail().contains("cover-grid"), "the answer names the missing template");
    }

    private static SeriesDraft withColour(SeriesDraft d, String colour) {
        return new SeriesDraft(d.name(), d.badgeText(), colour, d.badgeToken(), d.openingFormula(), d.layoutTemplate(),
                d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), d.active(), d.playlists());
    }

    private static SeriesDraft withTemplate(SeriesDraft d, String template) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(), template,
                d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), d.active(), d.playlists());
    }
}
