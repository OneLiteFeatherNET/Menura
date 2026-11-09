package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.Audience;
import net.onelitefeather.menura.core.series.SeriesDraft;
import net.onelitefeather.menura.core.publish.PostService.DraftCommand;
import net.onelitefeather.menura.core.publish.PostService.EpisodeCommand;
import net.onelitefeather.menura.core.publish.PostService.PlanCommand;
import net.onelitefeather.menura.core.publish.PostService.PlanItem;
import net.onelitefeather.menura.core.publish.PostService.PostDetail;
import net.onelitefeather.menura.core.publish.support.PublishFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.onelitefeather.menura.core.publish.support.PublishFixture.editor;
import static net.onelitefeather.menura.core.publish.support.PublishFixture.publisher;
import static net.onelitefeather.menura.core.publish.support.PublishFixture.publisherFromToken;
import static net.onelitefeather.menura.core.publish.support.PublishFixture.timeOnly;
import static net.onelitefeather.menura.core.publish.support.PublishFixture.titleOnly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The post rules of the hub: statuses, approval, planning, revisions, recycling, the calendar and the feed. */
class PostServiceTest {

    /** Monday 2026-11-09 at 18:00 in Berlin, the slot a Monday series takes in the week after the test start. */
    private static final Instant MONDAY_NEXT_WEEK = Instant.parse("2026-11-09T17:00:00Z");

    @Test
    void anEditorCreatesADraftWithTheEditorAsAuthor() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");

        PostDetail created = world.service.create(editor(), draft(channel, world.finishedVideo(), "Highlight"));

        assertEquals(PostStatus.DRAFT, created.post().status(), "a new post is a draft");
        assertEquals(PublishFixture.EDITOR, created.post().authorId(), "the editor is recorded as the author");
    }

    @Test
    void aRawMaterialAssetCannotBePlanned() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID raw = UUID.randomUUID();
        world.posts.registerAsset(raw, "raw_material");

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.create(editor(), draft(channel, raw, "Local file")));

        assertEquals(422, failure.status(), "a file that is not a finished asset is refused");
        assertTrue(failure.detail().contains("finished asset"), "the refusal states that only finished assets can be planned");
    }

    @Test
    void aSeriesFillsTheDraftFromItsDefaults() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID series = world.series("Quick Tips", channel);

        PostDetail planned = world.service.create(editor(), new DraftCommand(channel, world.finishedVideo(), null, series,
                null, null, null, null, null, null));

        assertEquals("Quick Tips", planned.post().title(), "the title comes from the series");
        assertEquals("Kurz erklärt:", planned.post().description(), "the description starts with the opening formula");
        assertEquals(List.of("#quicktips"), planned.post().hashtags(), "the series hashtag is filled in");
        assertEquals("PL-Quick Tips", planned.post().playlistId(), "the channel's playlist is filled in");
    }

    @Test
    void aLaterSeriesChangeDoesNotReachAPlannedPost() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID series = world.series("Quick Tips", channel);
        UUID post = world.service.create(editor(), new DraftCommand(channel, world.finishedVideo(), null, series, null,
                null, null, null, null, null)).post().id();

        world.series.update(null, series, new SeriesDraft("Quick Tips", Map.of("de", "Kurz erklärt"), "primary",
                "on-primary", "Neue Formel:", "short-9x16", "#quicktips", Audience.DEVELOPERS.value(), 1, 18, true, Map.of()));

        assertEquals("Kurz erklärt:", world.service.get(post).post().description(),
                "the planned post keeps the text it was planned with");
    }

    @Test
    void anEditorCannotApprove() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = inReview(world);

        ProblemException failure = assertThrows(ProblemException.class, () -> world.service.approve(editor(), post));

        assertEquals(403, failure.status(), "only the publisher role approves");
        assertEquals(PostStatus.IN_REVIEW, world.service.get(post).post().status(), "the status is unchanged");
    }

    @Test
    void approvalIsRefusedOutsideTheWebApplication() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = inReview(world);

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.approve(publisherFromToken(), post));

        assertEquals(403, failure.status(), "a token cannot approve, even with the publisher role");
    }

    @Test
    void aPublisherApprovesInTheWebApplication() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = inReview(world);

        PostDetail approved = world.service.approve(publisher(), post);

        assertEquals(PostStatus.APPROVED, approved.post().status(), "approval moves the post to approved");
        assertEquals(PublishFixture.PUBLISHER, approved.post().approvedBy(), "the publisher is recorded");
    }

    @Test
    void aDraftCannotBeScheduledWithoutApproval() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = world.service.create(editor(), draftAt(channel, world.finishedVideo(), MONDAY_NEXT_WEEK)).post().id();

        ProblemException failure = assertThrows(ProblemException.class, () -> world.service.schedule(publisher(), post));

        assertEquals(409, failure.status(), "a draft that skips approval cannot be scheduled");
        assertEquals(PostStatus.DRAFT, world.service.get(post).post().status(), "the status stays draft");
    }

    @Test
    void aTitleOverThePlatformLimitBlocksScheduling() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = world.service.create(editor(), draftAt(channel, world.finishedVideo(), MONDAY_NEXT_WEEK,
                "A title that is longer than the one hundred characters YouTube accepts for a video title, which is far too long")).post().id();
        world.service.submit(editor(), post);
        world.service.approve(publisher(), post);

        DryRunRefusal refusal = assertThrows(DryRunRefusal.class, () -> world.service.schedule(publisher(), post));

        assertEquals("title_too_long", refusal.problems().get(0).code(), "the dry run names the title limit");
        assertEquals(PostStatus.APPROVED, world.service.get(post).post().status(), "a refused schedule changes nothing");
    }

    @Test
    void aCollisionIsShownButDoesNotBlockScheduling() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID first = scheduled(world, channel, MONDAY_NEXT_WEEK, "First");
        UUID second = scheduled(world, channel, MONDAY_NEXT_WEEK, "Second");

        PostDetail detail = world.service.get(second);
        PostDetail firstDetail = world.service.get(first);

        assertEquals(PostStatus.SCHEDULED, detail.post().status(), "a collision does not block the schedule");
        assertTrue(firstDetail.problems().stream().anyMatch(problem -> problem.code().equals("collision")),
                "the collision is shown on the earlier post too");
    }

    @Test
    void aPlanOntoThreeChannelsStoresOnePostPerChannelAndNamesTheRefusedChannel() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID a = world.connectChannel("a");
        UUID b = world.connectChannel("b");
        UUID c = world.connectChannel("c");
        world.expireChannel(c);
        UUID clip = world.finishedVideo();

        PostService.PlanResult result = world.service.planChannels(editor(), new PlanCommand(clip, null, null, null, List.of(
                new PlanItem(a, MONDAY_NEXT_WEEK, "A", "text a", List.of(), null),
                new PlanItem(b, MONDAY_NEXT_WEEK.plusSeconds(3600), "B", "text b", List.of(), null),
                new PlanItem(c, MONDAY_NEXT_WEEK, "C", "text c", List.of(), null))));

        assertEquals(2, result.posts().size(), "the two usable channels get their own draft");
        assertEquals(List.of(c), result.refused().stream().map(PostService.Refusal::channelId).toList(),
                "the channel that needs reconnection is named");
        assertTrue(result.refused().get(0).reason().contains("reconnection"), "the refusal gives the reason");
    }

    @Test
    void theNextThreeEpisodesFillFreeSlotsAndNameTheTakenOne() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID series = world.series("Dev Diary", channel);
        scheduled(world, channel, MONDAY_NEXT_WEEK, "Taken");

        PostService.EpisodeResult result = world.service.planEpisodes(editor(), series,
                new EpisodeCommand(channel, world.finishedVideo(), null, 3));

        assertEquals(List.of(Instant.parse("2026-11-02T17:00:00Z"), Instant.parse("2026-11-16T17:00:00Z"),
                        Instant.parse("2026-11-23T17:00:00Z")),
                result.posts().stream().map(detail -> detail.post().scheduledAt()).toList(),
                "the episodes take the next free Mondays at 18:00 in Berlin");
        assertEquals(List.of(MONDAY_NEXT_WEEK), result.skipped().stream().map(PostService.SkippedSlot::scheduledAt).toList(),
                "the taken Monday of the next week is named as skipped");
    }

    @Test
    void aPlannedEpisodeKeepsItsDefaultsUntilTheEditorChangesThem() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID series = world.series("Dev Diary", channel);
        UUID episode = world.service.planEpisodes(editor(), series, new EpisodeCommand(channel, world.finishedVideo(), null, 1))
                .posts().get(0).post().id();

        PostDetail edited = world.service.edit(editor(), episode, titleOnly("Episode with a new title"));

        assertEquals("Episode with a new title", edited.post().title(), "the editor changes the title before approval");
        assertEquals("Kurz erklärt:", edited.post().description(), "the other defaults stay");
    }

    @Test
    void anApprovedPostIsChangedOnlyThroughARevision() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = inReview(world);
        world.service.approve(publisher(), post);

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.edit(editor(), post, titleOnly("Changed")));

        assertEquals(409, failure.status(), "an approved post refuses a direct edit");
    }

    @Test
    void aProposedRevisionIsStoredAndLeavesThePostUnchanged() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = scheduled(world, channel, MONDAY_NEXT_WEEK, "Scheduled");

        PostRevision revision = world.service.propose(editor(), post, timeOnly(MONDAY_NEXT_WEEK.plusSeconds(3600)),
                "The evening slot gets more views.");

        assertEquals(PostRevision.Status.PENDING, revision.status(), "the revision waits for a publisher");
        assertEquals(MONDAY_NEXT_WEEK, world.service.get(post).post().scheduledAt(), "the time is unchanged");
    }

    @Test
    void anAcceptedRevisionOfAScheduledPostReturnsItToReview() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = scheduled(world, channel, MONDAY_NEXT_WEEK, "Scheduled");
        PostRevision revision = world.service.propose(editor(), post, titleOnly("A better title"), "Clearer title.");

        PostDetail accepted = world.service.acceptRevision(publisher(), post, revision.id());

        assertEquals("A better title", accepted.post().title(), "the content changes");
        assertEquals(PostStatus.IN_REVIEW, accepted.post().status(), "the post goes back to review");
        assertNull(accepted.post().approvedBy(), "the earlier approval is cleared");
    }

    @Test
    void aRejectedRevisionKeepsThePostUnchanged() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = scheduled(world, channel, MONDAY_NEXT_WEEK, "Scheduled");
        PostRevision revision = world.service.propose(editor(), post, titleOnly("Other"), "Try this.");

        PostRevision rejected = world.service.rejectRevision(publisher(), post, revision.id());

        assertEquals(PostRevision.Status.REJECTED, rejected.status(), "the revision is marked rejected");
        assertEquals("Scheduled", world.service.get(post).post().title(), "the post is unchanged");
    }

    @Test
    void aRevisionCannotBeAcceptedFromAToken() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = scheduled(world, channel, MONDAY_NEXT_WEEK, "Scheduled");
        PostRevision revision = world.service.propose(editor(), post, titleOnly("Other"), "Try this.");

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.acceptRevision(publisherFromToken(), post, revision.id()));

        assertEquals(403, failure.status(), "accepting a revision needs the web application");
    }

    @Test
    void aRecycledPostReferencesTheOriginalAndTheOriginalIsUnchanged() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID first = world.connectChannel("first");
        UUID second = world.connectChannel("second");
        UUID clip = world.finishedVideo();
        UUID original = world.service.create(editor(), draft(first, clip, "Original")).post().id();
        Post before = world.service.get(original).post();

        PostDetail recycled = world.service.create(editor(), new DraftCommand(second, clip, null, null, original,
                "Recycled text", null, null, null, null));

        assertEquals(original, recycled.post().sourcePostId(), "the new post references the original");
        assertEquals(before, world.service.get(original).post(), "the original post is unchanged");
    }

    @Test
    void aRecycledPostMustReuseTheAssetOfItsSource() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID original = world.service.create(editor(), draft(channel, world.finishedVideo(), "Original")).post().id();

        ProblemException failure = assertThrows(ProblemException.class, () -> world.service.create(editor(),
                new DraftCommand(channel, world.finishedVideo(), null, null, original, "Other", null, null, null, null)));

        assertEquals(422, failure.status(), "a recycled post with another asset is refused");
    }

    @Test
    void theCalendarShowsAPostInTheBerlinWeekOfItsLocalTime() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        Instant lateSunday = Instant.parse("2026-11-08T23:30:00Z");
        world.service.create(editor(), draftAt(channel, world.finishedVideo(), lateSunday));

        CalendarRange nextWeek = CalendarRange.of(CalendarRange.View.WEEK, LocalDate.of(2026, 11, 9), 0, PublishFixture.BERLIN);
        CalendarRange thisWeek = CalendarRange.of(CalendarRange.View.WEEK, LocalDate.of(2026, 11, 2), 0, PublishFixture.BERLIN);

        assertEquals(1, world.service.calendar(nextWeek, null, null, null).items().size(),
                "23:30 UTC on Sunday is Monday 00:30 in Berlin, so the post is in the week of Monday");
        assertTrue(world.service.calendar(thisWeek, null, null, null).items().isEmpty(), "and not in the week before");
    }

    @Test
    void theCalendarFiltersByStatusAndKeepsPlannedAndPublishedTogether() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        scheduled(world, channel, MONDAY_NEXT_WEEK, "Planned");
        world.service.create(editor(), draftAt(channel, world.finishedVideo(), MONDAY_NEXT_WEEK.plusSeconds(7200), "Draft"));
        CalendarRange week = CalendarRange.of(CalendarRange.View.WEEK, LocalDate.of(2026, 11, 9), 0, PublishFixture.BERLIN);

        assertEquals(2, world.service.calendar(week, null, null, null).items().size(), "planned and draft posts share the view");
        assertEquals(1, world.service.calendar(week, null, null, PostStatus.SCHEDULED).items().size(),
                "the status filter narrows the view");
    }

    @Test
    void theFeedHoldsApprovalsOnlyForPublishers() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = planAt(world, channel, MONDAY_NEXT_WEEK);
        world.service.submit(editor(), post);

        long publisherApprovals = world.service.feedEvents(PublishFixture.NOW, true).stream()
                .filter(event -> event.uid().startsWith("approval-")).count();
        long editorApprovals = world.service.feedEvents(PublishFixture.NOW, false).stream()
                .filter(event -> event.uid().startsWith("approval-")).count();

        assertEquals(1, publisherApprovals, "a publisher sees the approval due");
        assertEquals(0, editorApprovals, "an editor who is not a publisher sees no approval");
    }

    @Test
    void aPostNeedsAChannelThatExists() throws Exception {
        PublishFixture world = new PublishFixture(false);

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.create(editor(), draft(UUID.randomUUID(), world.finishedVideo(), "Orphan")));

        assertEquals(422, failure.status(), "a post on an unknown channel is refused");
        assertTrue(world.posts.all().isEmpty(), "nothing is stored");
    }

    // ----------------------------------------------------------------- helpers

    private static DraftCommand draft(UUID channel, UUID asset, String title) {
        return new DraftCommand(channel, asset, null, null, null, title, null, null, Visibility.PRIVATE, null);
    }

    private static DraftCommand draftAt(UUID channel, UUID asset, Instant at) {
        return new DraftCommand(channel, asset, null, null, null, "Draft", null, null, Visibility.PRIVATE, at);
    }

    private static DraftCommand draftAt(UUID channel, UUID asset, Instant at, String title) {
        return new DraftCommand(channel, asset, null, null, null, title, null, null, Visibility.PRIVATE, at);
    }

    private static UUID planAt(PublishFixture world, UUID channel, Instant at) throws Exception {
        return world.service.create(editor(), draftAt(channel, world.finishedVideo(), at)).post().id();
    }

    private static UUID inReview(PublishFixture world) throws Exception {
        UUID channel = world.connectChannel("review");
        return world.service.submit(editor(), world.service.create(editor(), draft(channel, world.finishedVideo(), "Review"))
                .post().id()).post().id();
    }

    /** A post approved and scheduled at the given time. */
    private static UUID scheduled(PublishFixture world, UUID channel, Instant at, String title) throws Exception {
        UUID id = world.service.create(editor(), draftAt(channel, world.finishedVideo(), at, title)).post().id();
        world.service.declareMadeForKids(editor(), id, false);
        world.service.submit(editor(), id);
        world.service.approve(publisher(), id);
        world.service.schedule(publisher(), id);
        return id;
    }

}
