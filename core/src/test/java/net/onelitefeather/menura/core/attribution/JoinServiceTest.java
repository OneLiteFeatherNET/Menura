package net.onelitefeather.menura.core.attribution;

import net.onelitefeather.menura.core.attribution.JoinReports.Grouping;
import net.onelitefeather.menura.core.attribution.JoinReports.NewPlayers;
import net.onelitefeather.menura.core.attribution.JoinReports.Period;
import net.onelitefeather.menura.core.attribution.JoinReports.PostEstimate;
import net.onelitefeather.menura.core.attribution.JoinReports.SeriesEstimate;
import net.onelitefeather.menura.core.attribution.JoinReports.SeriesEstimates;
import net.onelitefeather.menura.core.auth.Role;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ingestion, answers, the reports and the estimates of the join attribution, with an injected clock and no database. */
class JoinServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 8);
    private static final Instant NOW = JoinFixture.NOW;
    private static final UUID ADMIN = UUID.fromString("1d2e3f40-5a6b-4c7d-8e9f-0a1b2c3d4e5f");
    private static final double DELTA = 1e-9;
    private static final Set<Role> ALL_ROLES = Set.of(Role.VIEWER, Role.EDITOR, Role.PUBLISHER, Role.OPERATOR, Role.ADMIN);

    @Test
    void anAcceptedFirstJoinIsStoredAndCountedOnce() throws Exception {
        JoinFixture world = new JoinFixture();

        JoinService.Recorded<JoinEvent> recorded = world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));

        assertTrue(recorded.created(), "a new event is created");
        assertEquals(1, world.store.count(DAY, "total"), "the first join is counted for its day");
    }

    @Test
    void aRepeatedEventWithTheSameContentCountsTheJoinOnce() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));

        JoinService.Recorded<JoinEvent> again = world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));

        assertFalse(again.created(), "the repeated event is not created again");
        assertEquals(1, world.store.count(DAY, "total"), "the join is counted once");
        assertEquals(1, world.store.eventCount(), "nothing new is stored");
    }

    @Test
    void theSameEventIdWithAnotherFirstJoinFlagIsRefusedAndTheStoredEventStays() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.recordJoin(
                JoinFixture.eventOf(JoinFixture.id(1), false, NOW, JoinFixture.PSEUDONYM)));

        assertEquals(409, refused.status(), "the same id with other content is a conflict");
        assertTrue(world.store.findEvent(null, JoinFixture.id(1)).orElseThrow().firstJoin(),
                "the stored event is unchanged");
    }

    @Test
    void theSameEventIdWithAnotherTimestampIsRefused() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));

        assertEquals(409, assertThrows(ProblemException.class, () -> world.service.recordJoin(
                JoinFixture.firstJoin(JoinFixture.id(1), NOW.plusSeconds(60)))).status(),
                "a different timestamp under the same id is a conflict");
    }

    @Test
    void aNonFirstJoinIsStoredButNotCountedAsANewPlayer() throws Exception {
        JoinFixture world = new JoinFixture();

        world.service.recordJoin(JoinFixture.eventOf(JoinFixture.id(2), false, NOW, JoinFixture.PSEUDONYM));

        assertEquals(1, world.store.eventCount(), "the event is stored for its id");
        assertEquals(0, world.store.count(DAY, "total"), "a join that is not a first join is not a new player");
    }

    @Test
    void fiveJoinsOfWhichThreeAreYoutubeOneFriendsAndOneUnansweredGiveTheStatedFigures() throws Exception {
        JoinFixture world = new JoinFixture();
        for (int i = 1; i <= 5; i++) {
            world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(i), NOW.plusSeconds(i)));
        }
        world.service.recordAnswer(JoinFixture.id(1), StatedSource.YOUTUBE);
        world.service.recordAnswer(JoinFixture.id(2), StatedSource.YOUTUBE);
        world.service.recordAnswer(JoinFixture.id(3), StatedSource.YOUTUBE);
        world.service.recordAnswer(JoinFixture.id(4), StatedSource.FRIENDS);

        Period day = world.service.newPlayers(DAY, DAY, Grouping.DAY).periods().get(0);

        assertEquals(5, day.total(), "five first joins");
        assertEquals(3, day.stated().get(StatedSource.YOUTUBE), "three stated youtube");
        assertEquals(1, day.stated().get(StatedSource.FRIENDS), "one stated friends");
        assertEquals(1, day.unanswered(), "one unanswered, derived as the total minus the stated sources");
        assertEquals(80.0, day.answerRate(), DELTA, "four of five answered is 80 percent");
    }

    @Test
    void theWeeklyTotalIsTheSumOfItsDailyTotals() throws Exception {
        JoinFixture world = new JoinFixture();
        for (int day = 2; day <= 8; day++) {
            Instant joinedAt = Instant.parse("2026-11-0" + day + "T12:00:00Z");
            world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(day), joinedAt));
        }

        NewPlayers weekly = world.service.newPlayers(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), Grouping.WEEK);
        NewPlayers daily = world.service.newPlayers(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), Grouping.DAY);

        long dailySum = daily.periods().stream().mapToLong(Period::total).sum();
        assertEquals(7, weekly.periods().get(0).total(), "the week counts all seven joins");
        assertEquals(dailySum, weekly.periods().get(0).total(), "the week's total equals the sum of its days");
    }

    @Test
    void aSourceAnsweredTwoHoursAfterTheJoinIsStored() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        world.clock.advance(Duration.ofHours(2));

        JoinService.Recorded<JoinAnswer> answer = world.service.recordAnswer(JoinFixture.id(1), StatedSource.YOUTUBE);

        assertTrue(answer.created(), "an answer within 24 hours is stored");
        assertEquals(StatedSource.YOUTUBE, world.store.answerOf(JoinFixture.id(1)).orElseThrow());
    }

    @Test
    void aSourceAnsweredTwentyFiveHoursAfterTheJoinIsRefusedAndStoresNothing() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        world.clock.advance(Duration.ofHours(25));

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.recordAnswer(JoinFixture.id(1), StatedSource.YOUTUBE));

        assertEquals(422, refused.status(), "an answer after 24 hours is refused");
        assertEquals(0, world.store.answerCount(), "nothing is stored");
        assertEquals(0, world.service.newPlayers(DAY, DAY, Grouping.DAY).periods().get(0).stated()
                .get(StatedSource.YOUTUBE), "the join stays unanswered");
    }

    @Test
    void aSourceForAnUnknownJoinIsNotFound() {
        JoinFixture world = new JoinFixture();

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.recordAnswer(JoinFixture.id(99), StatedSource.TWITCH));

        assertEquals(404, refused.status(), "an answer for a join that was never stored is not found");
    }

    @Test
    void aRepeatedIdenticalAnswerChangesNothing() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        world.service.recordAnswer(JoinFixture.id(1), StatedSource.TWITCH);

        JoinService.Recorded<JoinAnswer> again = world.service.recordAnswer(JoinFixture.id(1), StatedSource.TWITCH);

        assertFalse(again.created(), "the repeated answer is not stored again");
        assertEquals(1, world.store.count(DAY, "twitch"), "the answer is counted once");
    }

    @Test
    void aDifferentSecondAnswerIsRefusedAndTheFirstStands() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        world.service.recordAnswer(JoinFixture.id(1), StatedSource.TWITCH);

        assertEquals(409, assertThrows(ProblemException.class,
                () -> world.service.recordAnswer(JoinFixture.id(1), StatedSource.TIKTOK)).status(),
                "a different source is a conflict");
        assertEquals(StatedSource.TWITCH, world.store.answerOf(JoinFixture.id(1)).orElseThrow(),
                "twitch remains the only answer");
    }

    @Test
    void aLaterAnswerMovesTheJoinFromUnansweredToItsSourceWithoutChangingTheTotal() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        Period before = world.service.newPlayers(DAY, DAY, Grouping.DAY).periods().get(0);

        world.service.recordAnswer(JoinFixture.id(1), StatedSource.OTHER);
        Period after = world.service.newPlayers(DAY, DAY, Grouping.DAY).periods().get(0);

        assertEquals(1, before.unanswered(), "before the answer the join is unanswered");
        assertEquals(0, after.unanswered(), "after the answer it is not");
        assertEquals(before.total(), after.total(), "the day's total does not change");
        assertEquals(1, after.stated().get(StatedSource.OTHER), "the join is counted under its stated source");
    }

    @Test
    void anAcceptedJoinIsInTheDailyRollupAfterFiftyNineSeconds() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        world.clock.advance(Duration.ofSeconds(59));

        Period day = world.service.newPlayers(DAY, DAY, Grouping.DAY).periods().get(0);

        assertEquals(1, day.total(), "the join is visible in the rollup within 60 seconds of its acceptance");
    }

    @Test
    void aPostOfASeriesHiddenFromTheCallerAnswersNotFound() throws Exception {
        JoinFixture world = new JoinFixture();
        UUID post = JoinFixture.id(1);
        world.store.addPost(new AttributablePost(post, "youtube", NOW.minusSeconds(3600), JoinFixture.id(5), "Geheim",
                Set.of(Role.ADMIN)));

        ProblemException refused = assertThrows(ProblemException.class,
                () -> world.service.postEstimate(post, Set.of(Role.VIEWER)));

        assertEquals(404, refused.status(), "a post the caller may not see is not found");
    }

    @Test
    void aVisiblePostReadsItsEstimateWithTheWindowOfTheInstallation() throws Exception {
        JoinFixture world = new JoinFixture();
        UUID post = JoinFixture.id(1);
        world.store.addPost(new AttributablePost(post, "youtube", NOW.minusSeconds(3600), null, null, Set.of()));
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(2), NOW));

        PostEstimate estimate = world.service.postEstimate(post, Set.of(Role.VIEWER));

        assertEquals(1.0, estimate.estimatedFirstJoins(), DELTA, "the only post in the window receives the join");
        assertEquals(72, estimate.windowHours(), "the default window is 72 hours");
    }

    @Test
    void theSeriesSumsEqualThePostEstimatesAndPostsWithoutSeriesCountAsNoSeries() throws Exception {
        JoinFixture world = new JoinFixture();
        UUID seriesA = JoinFixture.id(50);
        UUID first = JoinFixture.id(1);
        UUID second = JoinFixture.id(2);
        UUID plain = JoinFixture.id(3);
        world.store.addPost(new AttributablePost(first, "youtube", NOW.minusSeconds(10 * 3600L), seriesA, "Serie A", ALL_ROLES));
        world.store.addPost(new AttributablePost(second, "tiktok", NOW.minusSeconds(20 * 3600L), seriesA, "Serie A", ALL_ROLES));
        world.store.addPost(new AttributablePost(plain, "youtube", NOW.minusSeconds(30 * 3600L), null, null, Set.of()));
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(9), NOW));

        SeriesEstimates series = world.service.seriesEstimates(DAY, DAY, Set.of(Role.VIEWER));
        double postSum = world.service.postEstimate(first, Set.of(Role.VIEWER)).estimatedFirstJoins()
                + world.service.postEstimate(second, Set.of(Role.VIEWER)).estimatedFirstJoins()
                + world.service.postEstimate(plain, Set.of(Role.VIEWER)).estimatedFirstJoins();
        double seriesSum = series.series().stream().mapToDouble(SeriesEstimate::estimatedFirstJoins).sum();

        assertEquals(2.0 / 3, series.series().get(0).estimatedFirstJoins(), DELTA, "series A has two of three posts");
        assertEquals(JoinReports.NO_SERIES, series.series().get(1).name(), "posts without a series come last");
        assertEquals(1.0 / 3, series.series().get(1).estimatedFirstJoins(), DELTA, "the post without a series has a third");
        assertEquals(postSum, seriesSum, DELTA, "the series values sum to the post estimates");
    }

    @Test
    void aRebuiltDayEqualsTheAcceptedFirstJoins() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(1), NOW));
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(2), NOW.plusSeconds(5)));
        world.service.recordAnswer(JoinFixture.id(1), StatedSource.YOUTUBE);
        world.store.addToDay(null, DAY, "total", 7);

        world.service.rebuildDay(DAY);

        assertEquals(2, world.store.count(DAY, "total"), "the rebuilt total equals the accepted first joins");
        assertEquals(1, world.store.count(DAY, "youtube"), "the rebuilt source counts the answered joins");
    }

    @Test
    void aJoinDomainWithUpperCaseLettersIsRefusedAndNothingIsStored() throws Exception {
        JoinFixture world = new JoinFixture();

        assertEquals(422, assertThrows(ProblemException.class,
                () -> world.service.saveSettings(ADMIN, "OneLiteFeather.net", null)).status(),
                "an upper-case domain is refused");
        assertNull(world.service.settings().joinDomain(), "no domain is stored");
    }

    @Test
    void aSecondDomainReplacesTheFirstAndKeepsTheWindow() throws Exception {
        JoinFixture world = new JoinFixture();
        world.service.saveSettings(ADMIN, "onelitefeather.net", 24);

        JoinSettings replaced = world.service.saveSettings(ADMIN, "play.onelitefeather.net", null);

        assertEquals("play.onelitefeather.net", replaced.joinDomain(), "the new domain replaces the old one");
        assertEquals(24, replaced.attributionWindowHours(), "the window keeps its value when none is sent");
        assertEquals(ADMIN, replaced.updatedBy(), "the admin who saved it is recorded");
    }

    @Test
    void anInstallationWindowChangesTheEstimate() throws Exception {
        JoinFixture world = new JoinFixture();
        UUID post = JoinFixture.id(1);
        world.store.addPost(new AttributablePost(post, "youtube", NOW.minusSeconds(30 * 3600L), null, null, Set.of()));
        world.service.recordJoin(JoinFixture.firstJoin(JoinFixture.id(2), NOW));
        world.service.saveSettings(ADMIN, "onelitefeather.net", 24);

        assertEquals(0.0, world.service.postEstimate(post, Set.of(Role.VIEWER)).estimatedFirstJoins(), DELTA,
                "a post 30 hours before is outside a 24-hour window");
    }

    @Test
    void aPeriodThatEndsBeforeItStartsIsRefused() {
        JoinFixture world = new JoinFixture();

        assertEquals(400, assertThrows(ProblemException.class,
                () -> world.service.newPlayers(DAY, DAY.minusDays(1), Grouping.DAY)).status(),
                "a reversed period is refused");
    }

    @Test
    void theWindowDefaultIsSeventyTwoHours() {
        assertEquals(Duration.ofHours(72), JoinSettings.unset().window(), "the default window is 72 hours");
    }
}
