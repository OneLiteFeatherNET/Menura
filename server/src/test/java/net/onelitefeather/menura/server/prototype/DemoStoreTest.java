package net.onelitefeather.menura.server.prototype;

import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each test builds its own store on a fixed clock, so the order of the tests does not matter. */
class DemoStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static DemoStore store() {
        return new DemoStore(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DemoStore storeAt(Instant now) {
        return new DemoStore(Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Model.Post post(DemoStore store, String id) {
        return store.posts(null, null).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static Model.SeriesRequest request(String name, String colorToken, String badgeText, String hashtag) {
        return new Model.SeriesRequest(name, new Model.Localized(name, name), colorToken, badgeText, "Kurz:",
                hashtag, "developers", "THURSDAY", "18:00", Map.of(), null, null);
    }

    private static ProblemException refused(Runnable action) {
        return assertThrows(ProblemException.class, action::run);
    }

    // The seed

    @Test
    void theSeedHasTheFourSeriesOfTheContract() {
        assertEquals(List.of("k8s-30s", "live-migration", "wir-bauen-olf", "survival"),
                store().series().stream().map(Model.Series::id).toList());
        assertEquals(List.of("#k8sin30", "#livemigration", "#wirbauenolf", "#olfsurvival"),
                store().series().stream().map(Model.Series::hashtag).toList());
    }

    @Test
    void theSeedHasEighteenPostsInTwoWeeksAcrossTheThreeChannels() {
        List<Model.Post> posts = store().posts(null, null);

        assertEquals(18, posts.size());
        assertEquals(List.of("2026-W44", "2026-W45"),
                posts.stream().map(p -> IsoWeek.of(Instant.parse(p.publishedAt()))).distinct().sorted().toList());
        assertEquals(Set.of("yt-de", "tt-de", "ig-de"), posts.stream().map(Model.Post::channelId).collect(
                java.util.stream.Collectors.toSet()));
    }

    @Test
    void everyStoreStartsWithTheSameSeedData() {
        assertEquals(store().posts(null, null), store().posts(null, null));
        assertEquals(store().coverage(), store().coverage());
    }

    @Test
    void episodesOfASeriesFollowThePublishingOrderFromOne() {
        List<Integer> episodes = store().posts("k8s-30s", null).stream().map(Model.Post::episode).toList();

        assertEquals(List.of(1, 2, 3, 4), episodes);
        assertEquals(3, post(store(), "p-43").episode(), "the Tetragon episode is the third one");
    }

    @Test
    void everySeedSeriesCountsItsFourEpisodes() {
        assertTrue(store().series().stream().allMatch(s -> s.episodes() == 4));
    }

    @Test
    void theOnlyWarningsOfTheSeedAreTheFlaggedCoverageAndTheMissingPlaylistEntry() {
        List<String> messages = store().warnings().stream().map(Model.Warning::message).toList();

        assertEquals(List.of(
                "yt-de: 2026-W45 nur 89 % der Shorts in einer Serie",
                "\"Autoscaling live\" fehlt 24 h nach Veröffentlichung in der Serien-Playlist"), messages);
    }

    @Test
    void thePlaylistWarningWaitsTwentyFourHoursAfterPublishing() {
        // Autoscaling live was published on 2026-11-03 at 16:00 UTC.
        assertTrue(storeAt(Instant.parse("2026-11-04T15:59:59Z")).warnings().stream()
                .noneMatch(w -> w.kind().equals("playlist")));
        assertTrue(storeAt(Instant.parse("2026-11-04T16:00:00Z")).warnings().stream()
                .anyMatch(w -> w.kind().equals("playlist")));
    }

    @Test
    void theRenderBundleCarriesTheKitVersionTheActiveSeriesAndTheChannels() {
        Model.RenderBundle bundle = store().renderBundle();

        assertEquals(1, bundle.kitVersion());
        assertEquals(4, bundle.series().size());
        assertEquals(3, bundle.channels().size());
        assertEquals(1, bundle.brandKit().version());
    }

    @Test
    void theSeedAttributesJoinsToChannelsAndDirectPerWeek() {
        assertEquals(List.of(
                new Model.WeeklyAttribution("2026-W44", "youtube", "yt-de", 2),
                new Model.WeeklyAttribution("2026-W44", "tiktok", "tt-de", 1),
                new Model.WeeklyAttribution("2026-W44", "instagram", "ig-de", 2),
                new Model.WeeklyAttribution("2026-W44", "direct", null, 2),
                new Model.WeeklyAttribution("2026-W45", "youtube", "yt-de", 3),
                new Model.WeeklyAttribution("2026-W45", "tiktok", "tt-de", 1)), store().attribution());
    }

    @Test
    void theEstimatedFirstJoinsOfAPostFollowTheWindowSplit() {
        // p-41 is the only youtube post in the window of the first join and half of the third one.
        assertEquals(1.5, post(store(), "p-41").estimatedFirstJoins(), 1e-9);
        assertEquals(1.0, post(store(), "p-50").estimatedFirstJoins(), 1e-9);
    }

    @Test
    void theSeriesColourIsTheLightColourOfItsToken() {
        Map<String, String> light = store().brandKit().tokens().stream()
                .collect(java.util.stream.Collectors.toMap(Model.Token::name, Model.Token::light));

        for (Model.Series series : store().series()) {
            assertEquals(light.get(series.colorToken()), series.color(), series.id());
        }
    }

    @Test
    void theBrandKitSlotsOfBadgeTitleAndCaptionsStayInsideTheSafeZone() {
        Model.Template template = store().brandKit().templates().get(0);
        double top = template.height() * template.safeZone().topPercent() / 100.0;
        double bottom = template.height() * (100 - template.safeZone().bottomPercent()) / 100.0;

        for (Model.Slot slot : template.slots()) {
            if (!List.of("badge", "title", "captions", "logo").contains(slot.name())) {
                continue;
            }
            assertTrue(slot.y() >= top, slot.name() + " starts inside the top safe zone");
            assertTrue(slot.y() + slot.h() <= bottom, slot.name() + " ends inside the bottom safe zone");
        }
    }

    // Episode numbers

    @Test
    void assigningAnUnassignedPostGivesTheNextEpisodeOfTheSeries() {
        DemoStore store = store();

        Model.Post assigned = store.assign("p-55", new Model.AssignRequest("k8s-30s", false));

        assertEquals("k8s-30s", assigned.seriesId());
        assertEquals(5, assigned.episode());
    }

    @Test
    void assigningTheSameSeriesAgainKeepsTheEpisodeNumber() {
        Model.Post same = store().assign("p-41", new Model.AssignRequest("k8s-30s", false));

        assertEquals(1, same.episode());
    }

    @Test
    void anEpisodeNumberIsNeverReusedWhenThePostComesBack() {
        DemoStore store = store();
        store.assign("p-41", new Model.AssignRequest("live-migration", false));

        Model.Post back = store.assign("p-41", new Model.AssignRequest("k8s-30s", false));

        assertNotEquals(1, back.episode(), "episode 1 of the Kubernetes series is not handed out again");
        assertEquals(5, back.episode(), "the next free number of the series");
    }

    @Test
    void markingAPostNoSeriesDropsItsEpisodeAndCountsAsCovered() {
        Model.Post marked = store().assign("p-41", new Model.AssignRequest(null, true));

        assertTrue(marked.noSeries());
        assertNull(marked.seriesId());
        assertNull(marked.episode());
    }

    @Test
    void assigningAnUnknownPostIsNotFound() {
        ProblemException problem = refused(() -> store().assign("p-999", new Model.AssignRequest("k8s-30s", false)));

        assertEquals(404, problem.status());
    }

    @Test
    void assigningToAnUnknownSeriesIsRefused() {
        ProblemException problem = refused(() -> store().assign("p-55", new Model.AssignRequest("nope", false)));

        assertEquals(422, problem.status());
    }

    @Test
    void aPostCannotBeMarkedNoSeriesAndAssignedAtOnce() {
        ProblemException problem = refused(() -> store().assign("p-55", new Model.AssignRequest("k8s-30s", true)));

        assertEquals(422, problem.status());
    }

    // Series creation

    @Test
    void aCreatedSeriesIsActiveAndTakesTheColourOfItsToken() {
        Model.Series created = store().createSeries(request("Ops am Abend", "series-live-migration", "#FFFFFF",
                "#opsamabend"));

        assertEquals("ops-am-abend", created.id());
        assertEquals("#6638E4", created.color());
        assertTrue(created.active());
        assertEquals(0, created.episodes());
    }

    @Test
    void aHashtagAlreadyUsedByAnotherSeriesIsRefusedIgnoringCase() {
        ProblemException problem = refused(() -> store().createSeries(request("Neu", "series-k8s", "#FFFFFF",
                "#K8SIN30")));

        assertEquals(422, problem.status());
        assertTrue(problem.detail().contains("k8s-30s"), problem.detail());
    }

    @Test
    void anUnknownColourTokenIsRefused() {
        ProblemException problem = refused(() -> store().createSeries(request("Neu", "series-nope", "#FFFFFF",
                "#neu")));

        assertEquals(422, problem.status());
    }

    @Test
    void aBadgeTextBelowTheMinimumContrastIsRefusedWithThePairAndTheRatio() {
        ProblemException problem = refused(() -> store().createSeries(request("Neu", "series-k8s", "#7FB0FF",
                "#neu")));

        assertEquals(422, problem.status());
        assertTrue(problem.detail().contains("#7FB0FF"), problem.detail());
        assertTrue(problem.detail().contains("#2F6FEB"), problem.detail());
        assertTrue(problem.detail().contains("2.08:1"), problem.detail());
    }

    @Test
    void aSeriesNameTakenAsIdentifierGetsASuffix() {
        Model.Series created = store().createSeries(request("Live-Migration", "series-live-migration", "#FFFFFF",
                "#livemigration2"));

        assertEquals("live-migration-2", created.id());
    }

    // Warnings

    @Test
    void aSeriesWithoutActivePlayerSeriesGetsTheNoPlayerWarning() {
        List<Model.Series> series = DemoSeed.series().stream()
                .map(s -> s.id().equals("survival") ? s.withActive(false) : s).toList();

        List<Model.Warning> warnings = WarningRules.of(List.of(), series, List.of(), NOW);

        assertEquals(List.of(new Model.Warning("no-player-series",
                "Keine aktive Serie für die Zielgruppe Spieler.")), warnings);
        assertFalse(WarningRules.of(List.of(), DemoSeed.series(), List.of(), NOW).stream()
                .anyMatch(w -> w.kind().equals("no-player-series")));
    }

    // Comparison

    @Test
    void theComparisonDefaultsToTheLatestWeekAndSortsByEngagedViews() {
        List<Model.SeriesComparison> rows = store().comparison(null);

        assertEquals(List.of("wir-bauen-olf", "survival", "live-migration", "k8s-30s"),
                rows.stream().map(Model.SeriesComparison::seriesId).toList());
        assertEquals(List.of(2830L, 2500L, 2350L, 1880L),
                rows.stream().map(Model.SeriesComparison::engagedViews).toList());
    }

    @Test
    void aMalformedWeekIsRefused() {
        assertEquals(422, refused(() -> store().comparison("2026-45")).status());
    }

    @Test
    void everyComparisonRowIsMarkedAsAnEstimate() {
        assertTrue(store().comparison("2026-W44").stream().allMatch(Model.SeriesComparison::estimate));
    }
}
