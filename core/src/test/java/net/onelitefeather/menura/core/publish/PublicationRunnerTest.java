package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.publish.PostService.DraftCommand;
import net.onelitefeather.menura.core.publish.PostService.PostDetail;
import net.onelitefeather.menura.core.publish.PublicationRunner.Report;
import net.onelitefeather.menura.core.publish.support.PublishFixture;
import net.onelitefeather.menura.core.publish.support.ScriptedAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.onelitefeather.menura.core.publish.support.PublishFixture.editor;
import static net.onelitefeather.menura.core.publish.support.PublishFixture.publisher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The publication job against the fake platform, with an injected clock: no test waits for time to pass. */
class PublicationRunnerTest {

    /** Monday 2026-11-09 at 18:00 in Berlin. */
    private static final Instant SLOT = Instant.parse("2026-11-09T17:00:00Z");
    private static final String TRANSIENT = PublicationRunner.RATE_LIMITED;

    @Test
    void aDuePostIsPublishedOnceAndRecordedAsAnExternalPost() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = scheduled(world, channel, SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        Report first = world.runner.runOnce();

        assertEquals(1, first.published(), "the due post is published");
        PostDetail published = world.service.get(post);
        assertEquals(PostStatus.PUBLISHED, published.post().status(), "the post is published");
        assertNotNull(published.post().externalUrl(), "the public URL is stored");
        assertEquals(1, world.externalPosts.listForChannel(null, channel).size(),
                "the publication is recorded as one external post");
        assertEquals(post, world.posts.originOf(null, world.externalPosts.listForChannel(null, channel).get(0).id()).orElseThrow(),
                "the external post names the Menura post it came from");

        Report second = world.runner.runOnce();

        assertEquals(0, second.claimed(), "a published post is never claimed again");
        assertEquals(1, world.youtube.requests().size(), "the platform is called once");
    }

    @Test
    void aPostNotYetDueIsLeftAlone() throws Exception {
        PublishFixture world = new PublishFixture(false);
        scheduled(world, world.connectChannel("main"), SLOT);

        Report report = world.runner.runOnce();

        assertEquals(0, report.claimed(), "a post before its time is not claimed");
        assertTrue(world.youtube.requests().isEmpty(), "the platform is not called before the time");
    }

    @Test
    void aTransientFailureIsRetriedWithGrowingDelaysAndEveryAttemptIsRecorded() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.youtube.nextPublish(ScriptedAdapter.rateLimited()).nextPublish(ScriptedAdapter.rateLimited());
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        assertEquals(1, world.runner.runOnce().retried(), "the first attempt is rate limited and scheduled for a retry");
        assertEquals(0, world.runner.runOnce().claimed(), "nothing is claimed before the retry delay has passed");
        world.clock.advance(Duration.ofMinutes(5));
        assertEquals(1, world.runner.runOnce().retried(), "the second attempt is rate limited too");
        world.clock.advance(Duration.ofMinutes(10));
        assertEquals(1, world.runner.runOnce().published(), "the third attempt succeeds");

        assertEquals(PostStatus.PUBLISHED, world.service.get(post).post().status(), "the post is published");
        assertEquals(3, world.youtube.requests().size(), "the platform was called once per attempt");
        assertEquals(List.of(AttemptRecord.RETRY_SCHEDULED, AttemptRecord.RETRY_SCHEDULED, AttemptRecord.PUBLISHED),
                world.posts.attempts(null, post).stream().map(AttemptRecord::outcome).toList(),
                "each attempt records its outcome");
    }

    @Test
    void theRetryLimitEndsInAFailureThatShowsItsReason() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of(PublishSettings.MAX_ATTEMPTS, "2")));
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.youtube.nextPublish(ScriptedAdapter.rateLimited()).nextPublish(ScriptedAdapter.rateLimited());
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));
        world.runner.runOnce();
        world.clock.advance(Duration.ofMinutes(5));

        world.runner.runOnce();

        Post failed = world.service.get(post).post();
        assertEquals(PostStatus.FAILED, failed.status(), "the last attempt marks the post failed");
        assertEquals(TRANSIENT, failed.failureReason(), "the failure shows its reason");
        assertEquals(2, failed.attempts(), "the attempt count is kept");
    }

    @Test
    void anAmbiguousOutcomeIsNotRetriedAutomatically() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.youtube.nextPublish(ScriptedAdapter.unavailable());
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));
        world.runner.runOnce();

        world.clock.advance(Duration.ofDays(1));
        Report later = world.runner.runOnce();

        assertEquals(0, later.claimed(), "an ambiguous failure is never claimed again");
        assertEquals(1, world.youtube.requests().size(), "the platform was called exactly once");
        assertEquals(PostStatus.FAILED, world.service.get(post).post().status(), "the post is failed with an unknown outcome");
        assertEquals(AttemptRecord.UNKNOWN, world.posts.attempts(null, post).get(0).outcome(), "the outcome is recorded as unknown");
    }

    @Test
    void aRefusedPublicationIsNotRetried() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.youtube.nextPublish(ScriptedAdapter.refused());
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        world.runner.runOnce();

        assertEquals(PostStatus.FAILED, world.service.get(post).post().status(), "a refused publication fails at once");
        assertEquals(PublicationRunner.REFUSED, world.service.get(post).post().failureReason(), "the reason is shown");
    }

    @Test
    void aClaimThatExpiredWithoutAnOutcomeEndsAsUnknownAndIsNotPublished() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));
        Post stored = world.posts.stored(post).orElseThrow();
        world.posts.update(null, stored.claimed("crashed-run", PublishFixture.NOW.plusSeconds(60), PublishFixture.NOW));
        world.posts.putAttempt(null, new AttemptRecord(post, 1, PublishFixture.NOW, null, null, null));

        Report report = world.runner.runOnce();

        assertEquals(0, report.published(), "the post is not published again");
        assertTrue(world.youtube.requests().isEmpty(), "the platform is not called for it");
        assertEquals(PostStatus.FAILED, world.service.get(post).post().status(), "the post is failed with an unknown outcome");
        assertEquals(AttemptRecord.UNKNOWN, world.posts.attempts(null, post).get(0).outcome(), "the attempt is marked unknown");
    }

    @Test
    void aPostLeasedByAnotherRunIsNotPublishedTwice() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));
        Post stored = world.posts.stored(post).orElseThrow();
        world.posts.update(null, stored.claimed("other-run", SLOT.plus(Duration.ofMinutes(10)), SLOT));

        Report report = world.runner.runOnce();

        assertEquals(0, report.claimed(), "a post with a live claim is left to its run");
        assertTrue(world.youtube.requests().isEmpty(), "the platform is not called by the second run");
    }

    @Test
    void theDryRunRunsAgainBeforePublicationAndBlocksOnAnExpiredConnection() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID channel = world.connectChannel("main");
        UUID post = scheduled(world, channel, SLOT);
        world.expireChannel(channel);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        world.runner.runOnce();

        assertTrue(world.youtube.requests().isEmpty(), "a blocked post is not sent to the platform");
        PostDetail failed = world.service.get(post);
        assertEquals(PostStatus.FAILED, failed.post().status(), "the post is failed");
        assertTrue(failed.post().failureReason().contains("connection_unusable"), "the reason names the blocking problem");
    }

    @Test
    void theStepRowIsWrittenBeforeThePlatformIsCalled() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        boolean[] seen = new boolean[1];
        world.youtube.beforePublish(() -> {
            seen[0] = world.posts.stepsOf(post).get(PostStep.UPLOAD).state() == StepState.RUNNING
                    && world.posts.attempts(null, post).get(0).outcome() == null;
        });
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        world.runner.runOnce();

        assertTrue(seen[0], "the upload step is running and the attempt is open when the platform is called");
    }

    @Test
    void aFailedStepShowsItsReasonAndTheAttemptCount() throws Exception {
        PublishFixture world = new PublishFixture(false);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        world.youtube.nextPublish(ScriptedAdapter.rateLimited());
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        world.runner.runOnce();

        StepRecord upload = world.posts.stepsOf(post).get(PostStep.UPLOAD);
        assertEquals(StepState.FAILED, upload.state(), "the step that failed is shown as failed");
        assertEquals(TRANSIENT, upload.reason(), "the step shows its reason");
        assertEquals(1, world.service.get(post).post().attempts(), "the attempt count is shown");
    }

    @Test
    void aNativeChannelIsHandedOverBeforeItsTimeAndConfirmedAtIt() throws Exception {
        PublishFixture world = new PublishFixture(true);
        UUID post = scheduled(world, world.connectChannel("main"), SLOT);
        Instant handover = SLOT.minus(Duration.ofHours(24));

        world.clock.advance(Duration.between(PublishFixture.NOW, handover.minusSeconds(60)));
        assertEquals(0, world.runner.runOnce().handedOver(), "the post is not handed over before the lead time");

        world.clock.advance(Duration.ofMinutes(1));
        assertEquals(1, world.runner.runOnce().handedOver(), "the post is handed over at the lead time");
        assertEquals(SLOT, world.youtube.requests().get(0).publishAt(), "the platform receives the scheduled time");
        Post handed = world.posts.stored(post).orElseThrow();
        assertEquals(PostStatus.SCHEDULED, handed.status(), "the post stays scheduled until its time");
        assertNotNull(handed.externalId(), "the platform item is stored");
        assertEquals(StepState.DONE, world.posts.stepsOf(post).get(PostStep.SCHEDULE).state(), "the schedule step is done");

        world.clock.advance(Duration.between(handover.plusSeconds(60), SLOT));
        assertEquals(0, world.runner.runOnce().published(), "the job does not publish a handed-over post itself");
        assertEquals(1, world.youtube.requests().size(), "the platform is called only for the handover");

        world.clock.advance(Duration.ofMinutes(1));
        world.youtube.nextStatus(PlatformAdapter.PublicationStatus.PUBLISHED);
        assertEquals(1, world.runner.runOnce().confirmed(), "the platform's status confirms the publication");
        assertEquals(PostStatus.PUBLISHED, world.service.get(post).post().status(), "the post is published");
    }

    @Test
    void aChannelWithoutNativeSchedulingIsPublishedAtItsTimeWithoutAPublishTime() throws Exception {
        PublishFixture world = new PublishFixture(false);
        scheduled(world, world.connectChannel("main"), SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        world.runner.runOnce();

        assertNull(world.youtube.requests().get(0).publishAt(), "the job publishes now, at the planned time");
    }

    // ----------------------------------------------------------------- helpers

    /** A post approved and scheduled at the given time on the channel. */
    private static UUID scheduled(PublishFixture world, UUID channel, Instant at) throws Exception {
        UUID id = world.service.create(editor(), new DraftCommand(channel, world.finishedVideo(), null, null, null,
                "Scheduled clip", "Text", List.of(), null, at)).post().id();
        world.service.submit(editor(), id);
        world.service.approve(publisher(), id);
        world.service.schedule(publisher(), id);
        return id;
    }
}
