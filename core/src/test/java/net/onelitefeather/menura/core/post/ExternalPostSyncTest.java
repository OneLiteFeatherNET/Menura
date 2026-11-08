package net.onelitefeather.menura.core.post;

import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.post.ExternalPostService.Outcome;
import net.onelitefeather.menura.core.post.ExternalPostService.SyncResult;
import net.onelitefeather.menura.core.post.support.ListingPlatform;
import net.onelitefeather.menura.core.post.support.PostFixture;
import net.onelitefeather.menura.core.problem.ProblemException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The re-sync of a channel's posts against a scripted listing, with an injected clock and no database. */
class ExternalPostSyncTest {

    private static final Instant T0 = ChannelFixture.NOW;
    private static final Instant OLD = T0.minus(Duration.ofDays(30));
    private static final Instant RECENT = T0.minus(Duration.ofDays(1));

    @Test
    void theImportRecordsEveryPublishedItemWithAllItsFields() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC1");
        world.platform.list(List.of(ListingPlatform.item("v1", "First stream", OLD)));

        SyncResult result = world.service.syncChannel(channel);

        assertEquals(Outcome.SYNCED, result.outcome(), "a complete listing completes the import");
        List<ExternalPost> posts = world.posts.listForChannel(null, channel);
        assertEquals(1, posts.size(), "one record for the one published item");
        ExternalPost post = posts.get(0);
        assertEquals(channel, post.channelId(), "the record names its channel");
        assertEquals("listing", post.platform(), "the record names its platform");
        assertEquals("v1", post.platformItemId(), "the record keeps the platform identifier");
        assertEquals("https://video.example.test/v1", post.url(), "the record keeps the platform URL");
        assertEquals("First stream", post.title(), "the record keeps the title");
        assertEquals("video", post.format(), "the record keeps the format");
        assertEquals(OLD, post.publishedAt(), "the record keeps the publish time");
        assertNull(post.removedAt(), "a listed post is live");
    }

    @Test
    void theImportRecordsEveryConnectedChannelAndNamesTheChannelOfEachRecord() throws SQLException {
        PostFixture world = new PostFixture();
        UUID first = world.connectedChannel("UC1");
        UUID second = world.connectedChannel("UC2");
        world.platform.list(List.of(ListingPlatform.item("v1", "Shared title", OLD)));

        world.service.syncDue(Duration.ZERO);

        assertEquals(1, world.posts.listForChannel(null, first).size(), "the first channel is imported");
        assertEquals(1, world.posts.listForChannel(null, second).size(), "the second channel is imported");
    }

    @Test
    void aChannelNeedingReconnectionIsSkippedAndShownAsWaiting() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.channel("UC3", ConnectionState.NEEDS_RECONNECTION);
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));

        SyncResult result = world.service.syncChannel(channel);

        assertEquals(Outcome.WAITING_FOR_RECONNECTION, result.outcome(), "the import reports the channel as waiting");
        assertTrue(world.posts.listForChannel(null, channel).isEmpty(), "no record is changed for that channel");
        assertEquals(0, world.posts.writes(), "no write happens for a channel that needs reconnection");
    }

    @Test
    void runningTheImportTwiceCreatesNoDuplicate() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC4");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD), ListingPlatform.item("v2", "Other", RECENT)));

        world.service.syncChannel(channel);
        world.service.syncChannel(channel);

        assertEquals(2, world.posts.listForChannel(null, channel).size(), "two items make two records, not four");
    }

    @Test
    void aNewVideoIsAddedOnTheNextRunAndTheExistingRecordsKeepTheirIdentifiers() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC5");
        world.platform.list(List.of(ListingPlatform.item("v1", "Old", OLD)));
        world.service.syncChannel(channel);
        UUID existing = world.posts.listForChannel(null, channel).get(0).id();

        world.platform.list(List.of(ListingPlatform.item("v1", "Old", OLD), ListingPlatform.item("v2", "New", RECENT)));
        SyncResult result = world.service.syncChannel(channel);

        assertEquals(1, result.added(), "one video was published since the previous run");
        assertEquals(existing, world.posts.listForChannel(null, channel).stream()
                .filter(post -> post.platformItemId().equals("v1")).findFirst().orElseThrow().id(),
                "the existing record keeps its identifier");
    }

    @Test
    void aChangedTitleUpdatesTheRecordWithoutASecondRecord() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC6");
        world.platform.list(List.of(ListingPlatform.item("v1", "Before", OLD)));
        world.service.syncChannel(channel);

        world.platform.list(List.of(ListingPlatform.item("v1", "After", OLD)));
        world.service.syncChannel(channel);

        List<ExternalPost> posts = world.posts.listForChannel(null, channel);
        assertEquals(1, posts.size(), "the title change adds no record");
        assertEquals("After", posts.get(0).title(), "the record shows the new title");
    }

    @Test
    void aRunOverAnUnchangedChannelWritesNothing() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC7");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));
        world.service.syncChannel(channel);
        int writesAfterImport = world.posts.writes();

        SyncResult result = world.service.syncChannel(channel);

        assertEquals(writesAfterImport, world.posts.writes(), "an unchanged channel writes no record again");
        assertEquals(0, result.added() + result.updated() + result.removed(), "the run reports no change");
    }

    @Test
    void aFailedListingLeavesTheRecordsUnchangedAndRecordsTheFailure() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC8");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));
        world.service.syncChannel(channel);
        ExternalPost before = world.posts.listForChannel(null, channel).get(0);
        world.platform.failListing(new PlatformException(PlatformException.Kind.UNAVAILABLE, "down", null));

        SyncResult result = world.service.syncChannel(channel);

        assertEquals(Outcome.FAILED, result.outcome(), "an unreachable platform fails the run");
        assertEquals(List.of(before), world.posts.listForChannel(null, channel), "the records are unchanged");
        ExternalPostStore.SyncStatus status = world.posts.findSync(null, channel).orElseThrow();
        assertNotNull(status.lastFailureAt(), "the failure is recorded for the channel");
        assertEquals(T0, status.lastFailureAt(), "the failure is stamped with the injected clock");
    }

    @Test
    void anItemMissingFromACompleteListingIsMarkedRemovedWithTheTimeSeen() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC9");
        world.platform.list(List.of(ListingPlatform.item("v1", "Gone soon", OLD), ListingPlatform.item("v2", "Stays", OLD)));
        world.service.syncChannel(channel);

        world.channels.clock.advance(Duration.ofHours(6));
        world.platform.list(List.of(ListingPlatform.item("v2", "Stays", OLD)));
        world.service.syncChannel(channel);

        ExternalPost gone = world.posts.listForChannel(null, channel).stream()
                .filter(post -> post.platformItemId().equals("v1")).findFirst().orElseThrow();
        assertEquals(T0.plus(Duration.ofHours(6)), gone.removedAt(), "the removal carries the time it was seen");
    }

    @Test
    void aRemovedPostKeepsItsRecordAndItsHistory() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC10");
        world.platform.list(List.of(ListingPlatform.item("v1", "Deleted on the platform", OLD)));
        world.service.syncChannel(channel);
        UUID id = world.posts.listForChannel(null, channel).get(0).id();

        world.platform.list(List.of());
        world.service.syncChannel(channel);

        ExternalPost kept = world.posts.stored(id).orElseThrow(
                () -> new AssertionError("a removed post must not be deleted"));
        assertTrue(kept.removed(), "the record is marked removed");
        assertEquals(1, world.posts.listForChannel(null, channel).size(), "no record disappears");
    }

    @Test
    void aRemovedPostListedAgainBecomesLive() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC11");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));
        world.service.syncChannel(channel);
        world.platform.list(List.of());
        world.service.syncChannel(channel);

        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));
        world.service.syncChannel(channel);

        ExternalPost post = world.posts.listForChannel(null, channel).get(0);
        assertFalse(post.removed(), "a later complete listing that contains the post makes it live again");
    }

    @Test
    void aListingThatFailsPartWayMarksNothingRemoved() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC12");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD), ListingPlatform.item("v2", "Other", OLD)));
        world.service.syncChannel(channel);
        world.platform.failListing(new PlatformException(PlatformException.Kind.RATE_LIMITED, "slow down", Duration.ofMinutes(5)));

        world.service.syncChannel(channel);

        assertTrue(world.posts.listForChannel(null, channel).stream().noneMatch(ExternalPost::removed),
                "a listing that broke part-way is no evidence of removal");
    }

    @Test
    void aTruncatedListingAddsItsItemsButMarksNothingRemoved() throws SQLException {
        PostFixture world = new PostFixture(2);
        UUID channel = world.connectedChannel("UC13");
        world.platform.list(List.of(ListingPlatform.item("v1", "One", OLD)));
        world.service.syncChannel(channel);

        world.platform.list(List.of(ListingPlatform.item("v2", "Two", RECENT), ListingPlatform.item("v3", "Three", RECENT)));
        SyncResult result = world.service.syncChannel(channel);

        assertEquals(Outcome.INCOMPLETE, result.outcome(), "a listing that reaches the limit is incomplete");
        assertEquals(0, result.removed(), "an incomplete listing removes nothing");
        assertTrue(world.posts.listForChannel(null, channel).stream().noneMatch(ExternalPost::removed),
                "v1 is not marked removed because the listing did not prove it gone");
    }

    @Test
    void aPublishedPostIsReadBackLikeAnImportedOne() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC14");
        PublishedItem item = ListingPlatform.item("pub1", "Published by Menura", RECENT);
        world.posts.insert(null, ExternalPost.create(UUID.randomUUID(), channel, ListingPlatform.PLATFORM, item, T0));
        world.platform.list(List.of(ListingPlatform.item("imp1", "Imported", OLD)));
        world.service.syncChannel(channel);

        List<ExternalPost> posts = world.posts.listForChannel(null, channel);

        assertEquals(2, posts.size(), "both posts are read from the same store");
        for (ExternalPost post : posts) {
            assertNotNull(post.url(), "every record holds the platform URL");
            assertNotNull(post.format(), "every record holds the format");
            assertNotNull(post.publishedAt(), "every record holds the publish time");
            assertEquals(channel, post.channelId(), "every record names its channel");
        }
    }

    @Test
    void aSyncRunDoesNotExceedTheConfiguredListingLimit() throws SQLException {
        PostFixture world = new PostFixture(3);
        UUID channel = world.connectedChannel("UC15");
        world.platform.list(List.of());

        world.service.syncChannel(channel);

        assertEquals(List.of(3), world.platform.limitsRequested(), "the listing asks for the configured limit");
    }

    @Test
    void syncDueSkipsAChannelWhoseLastCompleteSyncIsRecent() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC16");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));
        world.service.syncChannel(channel);
        world.channels.clock.advance(Duration.ofHours(1));

        ExternalPostService.SyncReport report = world.service.syncDue(Duration.ofHours(6));

        assertEquals(1, report.skipped(), "a channel synced an hour ago is not synced again within six hours");
        assertEquals(0, report.synced(), "nothing is synced in this run");
    }

    @Test
    void syncDueSyncsAChannelWhoseLastCompleteSyncIsOlderThanTheInterval() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC17");
        world.platform.list(List.of(ListingPlatform.item("v1", "Title", OLD)));
        world.service.syncChannel(channel);
        world.channels.clock.advance(Duration.ofHours(7));

        ExternalPostService.SyncReport report = world.service.syncDue(Duration.ofHours(6));

        assertEquals(1, report.synced(), "a channel synced seven hours ago is due");
    }

    @Test
    void aPlatformRejectionRecordsAFailureWithoutATokenInIt() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC18");
        world.platform.failListing(new PlatformException(PlatformException.Kind.REJECTED, "refused", null));

        world.service.syncChannel(channel);

        String reason = world.posts.findSync(null, channel).orElseThrow().failureReason();
        assertFalse(reason.contains("token-UC18"), "the recorded reason never carries a token");
    }

    @Test
    void anUnreachablePlatformIsNotRecordedAsASuccessfulSync() throws SQLException {
        PostFixture world = new PostFixture();
        UUID channel = world.connectedChannel("UC19");
        world.platform.failListing(new PlatformException(PlatformException.Kind.UNAVAILABLE, "unreachable", null));

        SyncResult result = world.service.syncChannel(channel);

        assertEquals(Outcome.FAILED, result.outcome(), "a failed listing is never reported as a successful run");
        assertNull(world.posts.findSync(null, channel).orElseThrow().lastSuccessAt(), "no success is recorded");
    }

    @Test
    void theOverviewAnswers404ForAChannelThatDoesNotExist() {
        PostFixture world = new PostFixture();

        ProblemException failure = assertThrows(ProblemException.class,
                () -> world.service.overview(UUID.randomUUID(), null, null, 50));

        assertEquals(404, failure.status(), "a missing channel is Not Found");
    }
}
