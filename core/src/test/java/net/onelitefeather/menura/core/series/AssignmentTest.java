package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.post.ExternalPostFormat;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.SeriesService.AssignmentCommand;
import net.onelitefeather.menura.core.series.support.SeriesFixture;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Assignment of external posts to series: by the import hook and by editors. */
class AssignmentTest {

    private static final UUID EDITOR = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");
    private static final UUID OTHER_EDITOR = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");

    @Test
    void aPostWithTheHashtagOfOneSeriesIsAssignedWhenTheImportCompletes() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries kube = world.service.create(null, SeriesFixture.draft("Kubernetes", "#kubernetes30s"));
        UUID channel = world.channel("UC1");

        world.service.imported(null, post(channel, "v1"), listed("v1", "Neu! #kubernetes30s heute"));

        SeriesAssignment assignment = world.series.findAssignment(null, post(channel, "v1").id()).orElseThrow();
        assertEquals(kube.id(), assignment.seriesId(), "the post is assigned to the series of its hashtag");
        assertNull(assignment.assignedBy(), "the import, not an editor, made the decision");
    }

    @Test
    void theHashtagMatchesAsAWholeWordIgnoringCase() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("Kubernetes", "#kube"));
        UUID channel = world.channel("UC1");

        world.service.imported(null, post(channel, "v1"), listed("v1", "Mehr #KUBEzeit"));

        assertTrue(world.series.findAssignment(null, post(channel, "v1").id()).isEmpty(),
                "a longer tag that only starts the same way is not the series hashtag");
    }

    @Test
    void aPostWithTheHashtagsOfTwoSeriesIsFlaggedAndAssignedToNeither() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("Kubernetes", "#kube"));
        world.service.create(null, SeriesFixture.draft("Migration", "#migration"));
        UUID channel = world.channel("UC1");

        world.service.imported(null, post(channel, "v1"), listed("v1", "#kube und #migration"));

        SeriesAssignment assignment = world.series.findAssignment(null, post(channel, "v1").id()).orElseThrow();
        assertNull(assignment.seriesId(), "the post belongs to no series until an editor decides");
        assertEquals(SeriesAssignment.Flag.TWO_SERIES, assignment.flag(), "the post is flagged for an editor");
        assertEquals(0, world.series.episodes(null, world.series.list(null).get(0).id()).size(),
                "a flagged post receives no episode number");
    }

    @Test
    void aPostWithoutAnySeriesHashtagGetsNoDecision() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, SeriesFixture.draft("Kubernetes", "#kube"));
        UUID channel = world.channel("UC1");

        world.service.imported(null, post(channel, "v1"), listed("v1", "Ohne Serie"));

        assertTrue(world.series.findAssignment(null, post(channel, "v1").id()).isEmpty(),
                "an unmatched post has no decision, so it is not covered");
    }

    @Test
    void anInactiveSeriesDoesNotReceiveImportedPosts() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        world.service.create(null, withActive(SeriesFixture.draft("Paused", "#paused"), false));
        UUID channel = world.channel("UC1");

        world.service.imported(null, post(channel, "v1"), listed("v1", "#paused"));

        assertTrue(world.series.findAssignment(null, post(channel, "v1").id()).isEmpty(),
                "only active series take imported posts");
    }

    @Test
    void anEditorMarksAPostNoSeriesAndItIsCovered() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        ExternalPost post = post(channel, "v1");
        world.posts.insert(null, post);

        SeriesAssignment assignment = world.service.assign(null, post.id(),
                new AssignmentCommand(null, true, false), EDITOR);

        assertTrue(assignment.noSeries(), "the post carries the no-series mark");
        assertTrue(assignment.covered(), "a post marked no series is covered");
        assertEquals(EDITOR, assignment.assignedBy(), "the editor who marked it is recorded");
    }

    @Test
    void aReassignmentWithReplaceMovesThePostAndRecordsTheEditorAndTheTime() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries first = world.service.create(null, SeriesFixture.draft("Erste", "#erste"));
        ContentSeries second = world.service.create(null, SeriesFixture.draft("Zweite", "#zweite"));
        UUID channel = world.channel("UC1");
        ExternalPost post = post(channel, "v1");
        world.posts.insert(null, post);
        world.service.assign(null, post.id(), new AssignmentCommand(first.id(), false, false), EDITOR);
        world.clock.advance(Duration.ofHours(2));

        SeriesAssignment moved = world.service.assign(null, post.id(),
                new AssignmentCommand(second.id(), false, true), OTHER_EDITOR);

        assertEquals(second.id(), moved.seriesId(), "the post belongs to the second series");
        assertEquals(OTHER_EDITOR, moved.assignedBy(), "the editor of the reassignment is recorded");
        assertEquals(world.clock.instant(), moved.assignedAt(), "the time of the reassignment is recorded");
        assertEquals(second.id(), world.series.findAssignment(null, post.id()).orElseThrow().seriesId(),
                "the stored decision is the second series only");
    }

    @Test
    void aSecondSeriesWithoutReplaceIsRefusedWithAConflictAndNothingChanges() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries first = world.service.create(null, SeriesFixture.draft("Erste", "#erste"));
        ContentSeries second = world.service.create(null, SeriesFixture.draft("Zweite", "#zweite"));
        UUID channel = world.channel("UC1");
        ExternalPost post = post(channel, "v1");
        world.posts.insert(null, post);
        world.service.assign(null, post.id(), new AssignmentCommand(first.id(), false, false), EDITOR);
        int writes = world.series.writes();

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.assign(null, post.id(),
                new AssignmentCommand(second.id(), false, false), EDITOR));

        assertEquals(409, refused.status(), "a second assignment without replace conflicts");
        assertEquals(writes, world.series.writes(), "the refusal writes nothing");
        assertEquals(first.id(), world.series.findAssignment(null, post.id()).orElseThrow().seriesId(),
                "the first assignment stays");
    }

    @Test
    void aCommandNamingNeitherSeriesNorNoSeriesIsRefused() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        UUID channel = world.channel("UC1");
        ExternalPost post = post(channel, "v1");
        world.posts.insert(null, post);

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.assign(null, post.id(),
                new AssignmentCommand(null, false, false), EDITOR));

        assertEquals(400, refused.status(), "an assignment must name exactly one decision");
    }

    @Test
    void assigningAMissingPostAnswersNotFound() {
        SeriesFixture world = new SeriesFixture();

        ProblemException refused = assertThrows(ProblemException.class, () -> world.service.assign(null,
                UUID.randomUUID(), new AssignmentCommand(null, true, false), EDITOR));

        assertEquals(404, refused.status(), "a post that does not exist answers 404");
    }

    @Test
    void theSameDecisionAgainIsNotAChange() throws SQLException {
        SeriesFixture world = new SeriesFixture();
        ContentSeries series = world.service.create(null, SeriesFixture.draft("Erste", "#erste"));
        UUID channel = world.channel("UC1");
        ExternalPost post = post(channel, "v1");
        world.posts.insert(null, post);
        world.service.assign(null, post.id(), new AssignmentCommand(series.id(), false, false), EDITOR);
        int writes = world.series.writes();

        world.service.assign(null, post.id(), new AssignmentCommand(series.id(), false, false), OTHER_EDITOR);

        assertEquals(writes, world.series.writes(), "repeating the decision writes nothing");
        assertFalse(world.series.findAssignment(null, post.id()).orElseThrow().noSeries(), "still the series");
    }

    private static ExternalPost post(UUID channel, String itemId) {
        return SeriesFixture.post(channel, itemId, ExternalPostFormat.SHORT, SeriesFixture.NOW.minus(Duration.ofDays(1)));
    }

    private static PublishedItem listed(String itemId, String description) {
        return new PublishedItem(itemId, "https://video.example.test/" + itemId, "Title " + itemId,
                SeriesFixture.NOW.minus(Duration.ofDays(1)), ExternalPostFormat.SHORT, description);
    }

    private static SeriesDraft withActive(SeriesDraft d, boolean active) {
        return new SeriesDraft(d.name(), d.badgeText(), d.colourToken(), d.badgeToken(), d.openingFormula(),
                d.layoutTemplate(), d.hashtag(), d.audience(), d.weekday(), d.postingSlot(), active, d.playlists());
    }
}
