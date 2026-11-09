package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.publish.DryRun.Problem;
import net.onelitefeather.menura.core.publish.PostService.DraftCommand;
import net.onelitefeather.menura.core.publish.support.PublishFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.onelitefeather.menura.core.publish.support.PublishFixture.editor;
import static net.onelitefeather.menura.core.publish.support.PublishFixture.publisher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Short format of a YouTube post (change add-youtube-integration, task 4.7). YouTube has no flag for a Short: a video is a
 * Short when it is vertical or square and at most three minutes long. A post planned as a Short is checked against that rule
 * only where the asset records its size and duration; where it does not, the check warns and never invents the metadata.
 */
class ShortFormatTest {

    private static final Instant SLOT = Instant.parse("2026-11-09T17:00:00Z");

    @Test
    void aVerticalShortWithinThreeMinutesHasNoProblem() {
        assertEquals(List.of(), DryRun.shortProblems(true, 1080, 1920, 45),
                "a vertical video of 45 seconds meets the Short rule");
    }

    @Test
    void aSquareShortHasNoProblem() {
        assertEquals(List.of(), DryRun.shortProblems(true, 1080, 1080, 180),
                "a square video of exactly three minutes meets the Short rule");
    }

    @Test
    void aLandscapeShortIsRefusedWithItsSize() {
        List<Problem> problems = DryRun.shortProblems(true, 1920, 1080, 45);

        assertTrue(problems.stream().anyMatch(problem -> "short_not_vertical".equals(problem.code())
                && DryRun.ERROR.equals(problem.severity()) && problem.message().contains("1920x1080")),
                "a landscape asset planned as a Short is refused and its size is named");
    }

    @Test
    void aShortLongerThanThreeMinutesIsRefusedWithItsDuration() {
        List<Problem> problems = DryRun.shortProblems(true, 1080, 1920, 181);

        assertTrue(problems.stream().anyMatch(problem -> "short_too_long".equals(problem.code())
                && DryRun.ERROR.equals(problem.severity()) && problem.message().contains("181")),
                "a Short of 181 seconds is refused and its duration is named");
    }

    @Test
    void aShortWithoutRecordedSizeIsWarnedNotRefused() {
        List<Problem> problems = DryRun.shortProblems(true, null, null, null);

        assertEquals(1, problems.size(), "one warning is shown for the unverified Short");
        assertEquals("short_unverified", problems.get(0).code(), "the warning names the missing metadata");
        assertEquals(DryRun.WARNING, problems.get(0).severity(), "missing metadata warns and does not block");
        assertFalse(DryRun.blocking(problems), "the post can still be scheduled");
    }

    @Test
    void aLongVideoCarriesNoShortMarkerAndIsNotChecked() {
        assertEquals(List.of(), DryRun.shortProblems(false, 1920, 1080, 600),
                "a long landscape video not planned as a Short has no Short rule to meet");
    }

    @Test
    void aLandscapeShortCannotBeScheduled() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID channel = world.connectChannel("main");
        UUID landscape = world.storedVideo(1_000_000L, 1920, 1080, 45);
        UUID post = world.service.create(editor(), new DraftCommand(channel, landscape, null, null, null, "Clip", "Text",
                List.of(), Visibility.PRIVATE, SLOT)).post().id();
        world.service.declareYouTube(editor(), post, false, true);
        world.service.submit(editor(), post);
        world.service.approve(publisher(), post);

        DryRunRefusal refused = assertThrows(DryRunRefusal.class, () -> world.service.schedule(publisher(), post),
                "a landscape asset planned as a Short cannot be scheduled");

        assertTrue(refused.problems().stream().anyMatch(problem -> "short_not_vertical".equals(problem.code())),
                "the orientation problem is named");
    }

    @Test
    void aVerticalShortCanBeScheduled() throws Exception {
        PublishFixture world = new PublishFixture(false, PublishSettings.from(Map.of()), true);
        UUID channel = world.connectChannel("main");
        UUID vertical = world.storedVideo(1_000_000L, 1080, 1920, 45);
        UUID post = world.service.create(editor(), new DraftCommand(channel, vertical, null, null, null, "Clip", "Text",
                List.of(), Visibility.PRIVATE, SLOT)).post().id();
        world.service.declareYouTube(editor(), post, false, true);
        world.service.submit(editor(), post);
        world.service.approve(publisher(), post);

        assertEquals(PostStatus.SCHEDULED, world.service.schedule(publisher(), post).post().status(),
                "a vertical Short within the limit is scheduled");
    }
}
