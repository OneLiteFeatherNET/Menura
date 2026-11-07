package net.onelitefeather.menura.server.prototype;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageRulesTest {

    private static final String WEEK_45 = "2026-11-03T16:00:00Z";

    @Test
    void eightOfNineCoveredShortsAreFlaggedAtEightyNinePercent() {
        List<Model.Post> posts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            posts.add(Fixtures.post("s" + i, "yt-de", WEEK_45, "survival", false));
        }
        posts.add(Fixtures.post("u", "yt-de", WEEK_45, null, false));

        Model.Coverage row = CoverageRules.of(posts).get(0);

        assertEquals(9, row.shorts());
        assertEquals(8, row.covered());
        assertEquals(0.89, row.share(), 1e-9, "the share is rounded to two decimals");
        assertTrue(row.flagged(), "below 90 % the channel-week is flagged");
    }

    @Test
    void exactlyNinetyPercentIsNotFlagged() {
        List<Model.Post> posts = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            posts.add(Fixtures.post("s" + i, "yt-de", WEEK_45, "survival", false));
        }
        posts.add(Fixtures.post("u", "yt-de", WEEK_45, null, false));

        Model.Coverage row = CoverageRules.of(posts).get(0);

        assertEquals(0.9, row.share(), 1e-9);
        assertFalse(row.flagged(), "the floor is 0.90 itself, so 0.90 is not below it");
    }

    @Test
    void aPostMarkedNoSeriesCountsAsCovered() {
        Model.Coverage row = CoverageRules.of(List.of(Fixtures.post("n", "yt-de", WEEK_45, null, true))).get(0);

        assertEquals(1, row.covered());
        assertFalse(row.flagged());
    }

    @Test
    void anUnassignedPostIsNotCovered() {
        Model.Coverage row = CoverageRules.of(List.of(Fixtures.post("u", "yt-de", WEEK_45, null, false))).get(0);

        assertEquals(0, row.covered());
        assertTrue(row.flagged());
    }

    @Test
    void coverageIsReportedPerChannelAndWeek() {
        List<Model.Post> posts = List.of(
                Fixtures.post("a", "yt-de", WEEK_45, "survival", false),
                Fixtures.post("b", "tt-de", WEEK_45, "survival", false),
                Fixtures.post("c", "yt-de", "2026-10-27T16:00:00Z", "survival", false));

        assertEquals(3, CoverageRules.of(posts).size());
    }
}
