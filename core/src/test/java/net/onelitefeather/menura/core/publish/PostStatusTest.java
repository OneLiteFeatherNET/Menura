package net.onelitefeather.menura.core.publish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostStatusTest {

    @Test
    void aDraftMovesOnlyToReview() {
        assertTrue(PostStatus.DRAFT.canMoveTo(PostStatus.IN_REVIEW), "a draft is submitted for review");
        assertFalse(PostStatus.DRAFT.canMoveTo(PostStatus.PUBLISHED), "a draft cannot skip to published");
    }

    @Test
    void aDraftCannotBeSetToPublishedDirectly() {
        assertFalse(PostStatus.DRAFT.canMoveTo(PostStatus.PUBLISHED), "the status skip is refused");
        assertFalse(PostStatus.DRAFT.canMoveTo(PostStatus.SCHEDULED), "a draft cannot be scheduled without approval");
    }

    @Test
    void theLifecycleRunsFromReviewThroughApprovalAndScheduleToPublished() {
        assertTrue(PostStatus.IN_REVIEW.canMoveTo(PostStatus.APPROVED));
        assertTrue(PostStatus.APPROVED.canMoveTo(PostStatus.SCHEDULED));
        assertTrue(PostStatus.SCHEDULED.canMoveTo(PostStatus.PUBLISHED));
    }

    @Test
    void aScheduledPostCanFailOrReturnToReview() {
        assertTrue(PostStatus.SCHEDULED.canMoveTo(PostStatus.FAILED));
        assertTrue(PostStatus.SCHEDULED.canMoveTo(PostStatus.IN_REVIEW), "an accepted revision re-enters review");
    }

    @Test
    void aPublishedPostHasNoTransition() {
        for (PostStatus target : PostStatus.values()) {
            assertFalse(PostStatus.PUBLISHED.canMoveTo(target), "a published post never moves to " + target);
        }
    }

    @Test
    void onlyDraftAndReviewPostsAreEditedByEditors() {
        assertTrue(PostStatus.DRAFT.editable(), "a draft is editable");
        assertTrue(PostStatus.IN_REVIEW.editable(), "a post in review is editable until approval");
        assertFalse(PostStatus.APPROVED.editable(), "an approved post is changed through a revision");
    }

    @Test
    void theWireNameParsesCaseInsensitively() {
        assertEquals(PostStatus.IN_REVIEW, PostStatus.parse(" In_Review ").orElseThrow(), "the wire name parses");
        assertFalse(PostStatus.parse("archived").isPresent(), "an unknown status is refused");
    }
}
