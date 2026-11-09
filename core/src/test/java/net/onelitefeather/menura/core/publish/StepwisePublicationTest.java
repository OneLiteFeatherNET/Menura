package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.StepwisePublisher.UploadStart;
import net.onelitefeather.menura.core.publish.PostService.DraftCommand;
import net.onelitefeather.menura.core.publish.PublicationRunner.Report;
import net.onelitefeather.menura.core.publish.support.PublishFixture;
import net.onelitefeather.menura.core.publish.support.ScriptedStepwise;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The step-wise YouTube publication of the runner against the scripted platform: no network, a fixed clock, and an in-memory
 * storage. Each test states one behaviour from the change's spec.
 */
class StepwisePublicationTest {

    /** Monday 2026-11-09 at 18:00 in Berlin. */
    private static final Instant SLOT = Instant.parse("2026-11-09T17:00:00Z");
    private static final long MIB = 1024L * 1024L;

    @Test
    void aTimedUploadIsSentPrivateWithThePublishTimeAndHandedOver() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID post = planned(world, world.connectChannel("main"), world.storedVideo(MIB), null, Visibility.UNLISTED, SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT.minus(Duration.ofHours(1))));

        Report report = world.runner.runOnce();

        assertEquals(1, report.handedOver(), "the upload is made within the lead time and handed over");
        UploadStart start = world.stepwise.starts().get(0);
        assertEquals(Visibility.PRIVATE, start.visibility(), "a timed upload is sent private");
        assertEquals(SLOT, start.publishAt(), "the scheduled time is sent as the publish time");
        assertEquals(Boolean.FALSE, start.madeForKids(), "the made-for-kids answer is sent explicitly");
        assertTrue(world.service.get(post).post().handedOver(), "the post is handed to the platform for its own publication");
    }

    @Test
    void aLostUploadResponseResumesFromTheConfirmedByteWithoutANewSession() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        world.stepwise.loseResponseOnChunk(2);
        planned(world, world.connectChannel("main"), world.storedVideo(17 * MIB), null, Visibility.PRIVATE, SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        assertEquals(1, world.runner.runOnce().retried(), "the lost response is retried");
        world.clock.advance(Duration.ofMinutes(5));
        assertEquals(1, world.runner.runOnce().published(), "the retry completes the upload");

        assertEquals(1, world.stepwise.starts().size(), "the retry resumes the session, it does not start a new upload");
        assertEquals(3, world.stepwise.chunkCalls(), "the resumed upload sends only the bytes the platform did not hold");
        assertEquals(17 * MIB, world.stepwise.received(), "every byte is sent exactly once");
    }

    @Test
    void aFailedPlaylistStepIsRetriedAloneAndTheVideoIsNotUploadedAgain() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID channel = world.connectChannel("main");
        UUID series = world.series("Kurz", channel);
        UUID playlistPost = world.service.create(editor(), new DraftCommand(channel, world.storedVideo(MIB), null, series,
                null, "Clip", "Text", List.of(), Visibility.PRIVATE, SLOT)).post().id();
        world.service.declareMadeForKids(editor(), playlistPost, false);
        world.service.submit(editor(), playlistPost);
        world.service.approve(publisher(), playlistPost);
        world.service.schedule(publisher(), playlistPost);
        world.stepwise.failNextPlaylist(new PlatformException(PlatformException.Kind.UNAVAILABLE, "down", null));
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        assertEquals(1, world.runner.runOnce().retried(), "the playlist step fails and is scheduled for a retry");
        world.clock.advance(Duration.ofMinutes(5));
        assertEquals(1, world.runner.runOnce().published(), "the retry of the playlist step completes the post");

        assertEquals(1, world.stepwise.starts().size(), "the video is uploaded once");
        assertEquals(1, world.stepwise.chunkCalls(), "the bytes are sent once");
        assertEquals(List.of("PL-Kurz/" + ScriptedStepwise.VIDEO_ID), world.stepwise.playlistInserts(),
                "the playlist insert is attempted again and succeeds");
        assertEquals(PostStatus.PUBLISHED, world.service.get(playlistPost).post().status(), "the post is published");
    }

    @Test
    void aVisibilityApplyingOtherThanTheRequestedOneIsKeptAsAProblem() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        world.stepwise.applyVisibility(Visibility.PRIVATE);
        UUID post = planned(world, world.connectChannel("main"), world.storedVideo(MIB), null, Visibility.UNLISTED, SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        assertEquals(1, world.runner.runOnce().published(), "the post is published");

        PostUpload upload = world.posts.upload(null, post).orElseThrow();
        assertEquals("private", upload.appliedVisibility(), "the applied visibility is read back");
        assertNotNull(upload.visibilityProblem(), "a visibility that differs from the request is a problem, not a success");
    }

    @Test
    void aThumbnailWaitsForProcessingAndIsSetAfterIt() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        world.stepwise.processing("processing");
        planned(world, world.connectChannel("main"), world.storedVideo(MIB), world.storedCover(), Visibility.PRIVATE, SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        assertEquals(1, world.runner.runOnce().waiting(), "the post waits while the platform processes the video");
        assertTrue(world.stepwise.thumbnails().isEmpty(), "no thumbnail is set before processing has finished");

        world.stepwise.processing("succeeded");
        world.clock.advance(Duration.ofMinutes(1));
        assertEquals(1, world.runner.runOnce().published(), "the post is published once the thumbnail is set");
        assertEquals(List.of("image/jpeg:200000"), world.stepwise.thumbnails(), "the cover is set as the thumbnail");
    }

    @Test
    void anUploadIsRefusedBeforeThePlatformIsCalledWhenTheQuotaIsUsedUp() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID channel = world.connectChannel("main");
        world.quotaUsage.save(null, channel, QuotaBudget.day(SLOT), new QuotaLedger.Usage(9_990, 0));
        UUID post = planned(world, channel, world.storedVideo(MIB), null, Visibility.PRIVATE, SLOT);
        world.clock.advance(Duration.between(PublishFixture.NOW, SLOT));

        assertEquals(1, world.runner.runOnce().retried(), "the delayed upload is deferred, not failed");
        assertTrue(world.stepwise.starts().isEmpty(), "the platform is not called when the quota would be exceeded");
        assertEquals(QuotaBudget.nextReset(SLOT), world.posts.stored(post).orElseThrow().nextAttemptAt(),
                "the publication is attempted again after the quota resets at Pacific midnight");

        world.clock.advance(Duration.between(SLOT, QuotaBudget.nextReset(SLOT)));
        assertEquals(1, world.runner.runOnce().published(), "the delayed publication is attempted after the reset");
    }

    @Test
    void schedulingIsRefusedWhileTheMadeForKidsAnswerIsMissing() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID id = world.service.create(editor(), new DraftCommand(world.connectChannel("main"), world.storedVideo(MIB), null,
                null, null, "Clip", "Text", List.of(), Visibility.PRIVATE, SLOT)).post().id();
        world.service.submit(editor(), id);
        world.service.approve(publisher(), id);

        DryRunRefusal refused = assertThrows(DryRunRefusal.class, () -> world.service.schedule(publisher(), id),
                "a post without the made-for-kids answer cannot be scheduled");

        assertTrue(refused.problems().stream().anyMatch(problem -> "made_for_kids_missing".equals(problem.code())),
                "the missing made-for-kids answer is named");
    }

    @Test
    void schedulingIsRefusedWhileTheVisibilityWasNeverChosen() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID id = world.service.create(editor(), new DraftCommand(world.connectChannel("main"), world.storedVideo(MIB), null,
                null, null, "Clip", "Text", List.of(), null, SLOT)).post().id();
        world.service.declareMadeForKids(editor(), id, false);
        world.service.submit(editor(), id);
        world.service.approve(publisher(), id);

        DryRunRefusal refused = assertThrows(DryRunRefusal.class, () -> world.service.schedule(publisher(), id),
                "a post whose visibility nobody chose cannot be scheduled");

        assertTrue(refused.problems().stream().anyMatch(problem -> "visibility_undeclared".equals(problem.code())),
                "the missing visibility is named");
    }

    // ----------------------------------------------------------------- helpers

    private static UUID planned(PublishFixture world, UUID channel, UUID asset, UUID cover, Visibility visibility,
                                Instant at) throws Exception {
        UUID id = world.service.create(editor(), new DraftCommand(channel, asset, cover, null, null, "Clip", "Text",
                List.of(), visibility, at)).post().id();
        world.service.declareMadeForKids(editor(), id, false);
        world.service.submit(editor(), id);
        world.service.approve(publisher(), id);
        world.service.schedule(publisher(), id);
        return id;
    }
}
