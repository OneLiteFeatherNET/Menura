package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000001");
    private static final UUID OTHER_CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000002");
    private static final UUID ASSET = UUID.fromString("a55e7000-0000-4000-8000-000000000001");
    private static final UUID EDITOR = UUID.fromString("e0170000-0000-4000-8000-000000000001");
    private static final UUID PUBLISHER = UUID.fromString("b0b00000-0000-4000-8000-000000000001");
    private static final Instant SLOT = Instant.parse("2026-11-09T17:00:00Z");

    @Test
    void aNewDraftIsStoredWithItsAuthorAndTheDraftStatus() {
        Post post = draft("Stream highlight", List.of("#gaming"));

        assertEquals(PostStatus.DRAFT, post.status(), "a new post is a draft");
        assertEquals(EDITOR, post.authorId(), "the editor is recorded as the author");
        assertEquals(List.of("#gaming"), post.hashtags(), "the hashtags are kept");
        assertEquals(Visibility.PRIVATE, post.visibility(), "a post is private unless a person makes it otherwise");
    }

    @Test
    void hashtagsGetOneLeadingHashAndNoDuplicates() {
        Post post = draft("Clip", List.of("gaming", "#gaming", "clip"));

        assertEquals(List.of("#gaming", "#clip"), post.hashtags(), "a tag is stored once, with a leading hash");
    }

    @Test
    void aTitleThatIsBlankIsRefused() {
        ProblemException failure = assertThrows(ProblemException.class, () -> draft("   ", List.of()));

        assertEquals(400, failure.status(), "a blank title is a bad request");
    }

    @Test
    void anEditChangesOnlyTheFieldsGiven() {
        Post post = draft("Old", List.of("#a"));

        Post edited = post.edited(new Content(null, null, "New", null, null, null, null), null, NOW);

        assertEquals("New", edited.title(), "the given title is changed");
        assertEquals(List.of("#a"), edited.hashtags(), "the hashtags stay as they were");
        assertEquals(PostStatus.DRAFT, edited.status(), "an edit keeps the status");
    }

    @Test
    void anApprovedPostIsNotEditedDirectly() {
        Post approved = approved(draft("Text", List.of()));

        ProblemException failure = assertThrows(ProblemException.class,
                () -> approved.edited(new Content(null, null, "Changed", null, null, null, null), null, NOW));

        assertEquals(409, failure.status(), "an approved post refuses a direct edit");
    }

    @Test
    void approvalRecordsThePublisherAndMovesToApproved() {
        Post reviewed = draft("Text", List.of()).submitted(NOW);

        Post approved = reviewed.approved(PUBLISHER, NOW);

        assertEquals(PostStatus.APPROVED, approved.status(), "approval moves the post to approved");
        assertEquals(PUBLISHER, approved.approvedBy(), "the approval records the publisher");
        assertEquals(NOW, approved.approvedAt(), "the approval records its time");
    }

    @Test
    void aDraftCannotBeApprovedWithoutReview() {
        Post draft = draft("Text", List.of());

        ProblemException failure = assertThrows(ProblemException.class, () -> draft.approved(PUBLISHER, NOW));

        assertEquals(409, failure.status(), "a draft that was not submitted cannot be approved");
    }

    @Test
    void schedulingNeedsATimeAndAnApproval() {
        Post approved = approved(draft("Text", List.of()));

        ProblemException failure = assertThrows(ProblemException.class, () -> approved.scheduled(NOW));

        assertEquals(422, failure.status(), "a post without a scheduled time cannot be scheduled");
    }

    @Test
    void aRevisionOfAnApprovedPostReturnsItToReviewAndClearsTheApproval() {
        Post scheduled = approved(withTime(draft("Text", List.of()), SLOT)).scheduled(NOW);

        Post revised = scheduled.revised(new Content(null, null, null, "New text", null, null, null), null, NOW);

        assertEquals(PostStatus.IN_REVIEW, revised.status(), "an accepted revision of a scheduled post goes back to review");
        assertEquals("New text", revised.description(), "the revised text is stored");
        assertNull(revised.approvedBy(), "the earlier approval is cleared");
    }

    @Test
    void aRevisionOfADraftKeepsItAsADraft() {
        Post draft = draft("Text", List.of());

        Post revised = draft.revised(new Content(null, null, "Better", null, null, null, null), null, NOW);

        assertEquals(PostStatus.DRAFT, revised.status(), "a draft stays a draft after a revision");
    }

    @Test
    void aPublishedPostRecordsItsExternalReference() {
        Post scheduled = approved(withTime(draft("Text", List.of()), SLOT)).scheduled(NOW);

        Post published = scheduled.published("yt-1", "https://video.example.test/yt-1", SLOT, SLOT);

        assertEquals(PostStatus.PUBLISHED, published.status(), "the post is published");
        assertEquals("yt-1", published.externalId(), "the platform identifier is stored");
        assertEquals("https://video.example.test/yt-1", published.externalUrl(), "the public URL is stored");
    }

    @Test
    void aHandedOverPostKeepsItsPlatformReferenceWhenItIsRescheduled() {
        Post handed = approved(withTime(draft("Text", List.of()), SLOT)).scheduled(NOW)
                .handedOver("yt-7", "https://video.example.test/yt-7", NOW);
        Post failed = handed.failed("The platform failed.", NOW);

        Post again = failed.scheduled(NOW);

        assertEquals("yt-7", again.externalId(), "a reschedule never drops the platform item of a handed-over post");
        assertTrue(again.handedOver(), "the handover is still recorded");
    }

    @Test
    void aRetryKeepsThePostScheduledAndSetsItsNextAttempt() {
        Post scheduled = approved(withTime(draft("Text", List.of()), SLOT)).scheduled(NOW);
        Instant next = NOW.plusSeconds(300);

        Post retry = scheduled.retryAt("The platform is busy.", next, NOW);

        assertEquals(PostStatus.SCHEDULED, retry.status(), "a transient failure keeps the post scheduled");
        assertEquals(next, retry.nextAttemptAt(), "the next attempt is recorded");
        assertEquals("The platform is busy.", retry.failureReason(), "the reason is shown");
    }

    private static Post draft(String title, List<String> hashtags) {
        return Post.draft(UUID.nameUUIDFromBytes(title.getBytes()), EDITOR, new PostDraft(CHANNEL, ASSET, null, null,
                null, title, null, hashtags, null, null, null), NOW);
    }

    private static Post withTime(Post post, Instant at) {
        return post.edited(new Content(null, null, null, null, null, null, at), null, NOW);
    }

    private static Post approved(Post post) {
        return post.submitted(NOW).approved(PUBLISHER, NOW);
    }
}
