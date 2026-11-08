package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each dry-run check on its own, with a fake platform rule set and the facts passed in. */
class DryRunTest {

    private static final Instant SLOT = Instant.parse("2026-11-09T17:00:00Z");
    private static final UUID CHANNEL = UUID.fromString("c0ffee00-0000-4000-8000-000000000001");
    private static final PublishRules FAKE = new PublishRules(10, 20, 2, Set.of("video"));

    @Test
    void aCleanPostHasNoProblems() {
        List<DryRun.Problem> problems = check(post("Short", "Text", List.of("#a"), SLOT), ConnectionState.CONNECTED,
                "video", true, List.of());

        assertTrue(problems.isEmpty(), "a clean post shows no problems but found " + problems);
    }

    @Test
    void aTitleOverTheLimitIsAnError() {
        List<DryRun.Problem> problems = check(post("A title that is too long", "Text", List.of(), SLOT),
                ConnectionState.CONNECTED, "video", true, List.of());

        assertEquals("title_too_long", problems.get(0).code(), "the length limit is named");
        assertEquals(DryRun.ERROR, problems.get(0).severity(), "a length problem blocks");
    }

    @Test
    void aDescriptionOverTheLimitIsAnError() {
        List<DryRun.Problem> problems = check(post("Short", "x".repeat(21), List.of(), SLOT),
                ConnectionState.CONNECTED, "video", true, List.of());

        assertEquals("description_too_long", problems.get(0).code(), "the text limit is named");
    }

    @Test
    void tooManyHashtagsAreAnError() {
        List<DryRun.Problem> problems = check(post("Short", "Text", List.of("#a", "#b", "#c"), SLOT),
                ConnectionState.CONNECTED, "video", true, List.of());

        assertEquals("too_many_hashtags", problems.get(0).code(), "the hashtag limit is named");
    }

    @Test
    void aFormatTheChannelDoesNotTakeIsAnError() {
        List<DryRun.Problem> problems = check(post("Short", "Text", List.of(), SLOT), ConnectionState.CONNECTED,
                "image", true, List.of());

        assertEquals("format_not_allowed", problems.get(0).code(), "the format is named");
    }

    @Test
    void aChannelThatNeedsReconnectionShowsAReconnectAction() {
        List<DryRun.Problem> problems = check(post("Short", "Text", List.of(), SLOT),
                ConnectionState.NEEDS_RECONNECTION, "video", true, List.of());

        DryRun.Problem problem = problems.get(0);
        assertEquals("connection_unusable", problem.code(), "the connection problem is named");
        assertEquals(DryRun.RECONNECT, problem.action(), "the problem offers a reconnect action");
    }

    @Test
    void publicVisibilityNeedsAnAuditedApp() {
        Post post = Post.draft(UUID.randomUUID(), UUID.randomUUID(), new PostDraft(CHANNEL, UUID.randomUUID(), null,
                null, null, "Short", null, List.of(), Visibility.PUBLIC, null, SLOT), SLOT);

        List<DryRun.Problem> problems = DryRun.check(new DryRun.Input(post, ConnectionState.CONNECTED, "video", FAKE,
                false, List.of()));

        assertEquals("audit_restricted", problems.get(0).code(), "public visibility is refused without an audit");
    }

    @Test
    void twoPostsAtTheSameTimeOnOneChannelCollideOnBoth() {
        Post mine = post("Mine", "Text", List.of(), SLOT);
        Post theirs = post("Theirs", "Text", List.of(), SLOT);

        List<DryRun.Problem> problems = DryRun.check(new DryRun.Input(mine, ConnectionState.CONNECTED, "video", FAKE,
                true, List.of(theirs)));

        assertEquals("collision", problems.get(0).code(), "the collision is shown on the post");
        assertFalse(DryRun.blocking(problems), "a collision is a warning and does not block");
    }

    @Test
    void aFailedPostDoesNotCollide() {
        Post mine = post("Mine", "Text", List.of(), SLOT);
        Post failed = post("Failed", "Text", List.of(), SLOT).submitted(SLOT).approved(UUID.randomUUID(), SLOT)
                .scheduled(SLOT).failed("reason", SLOT);

        List<DryRun.Problem> problems = DryRun.check(new DryRun.Input(mine, ConnectionState.CONNECTED, "video", FAKE,
                true, List.of(failed)));

        assertTrue(problems.isEmpty(), "a failed post does not collide with a new one");
    }

    private static List<DryRun.Problem> check(Post post, ConnectionState state, String kind, boolean audited,
                                              List<Post> others) {
        return DryRun.check(new DryRun.Input(post, state, kind, FAKE, audited, others));
    }

    private static Post post(String title, String description, List<String> hashtags, Instant at) {
        return Post.draft(UUID.nameUUIDFromBytes(title.getBytes()), UUID.randomUUID(), new PostDraft(CHANNEL,
                UUID.randomUUID(), null, null, null, title, description, hashtags, null, null, at), at);
    }
}
