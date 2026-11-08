package net.onelitefeather.menura.core.post;

import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;
import net.onelitefeather.menura.core.channel.support.ChannelFixture;
import net.onelitefeather.menura.core.post.support.ListingPlatform;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPostTest {

    private static final UUID ID = UUID.fromString("0f1e2d3c-4b5a-4968-8778-6758493a2b1c");
    private static final UUID CHANNEL = UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private static final Instant PUBLISHED = ChannelFixture.NOW.minus(Duration.ofDays(3));
    private static final Instant LATER = ChannelFixture.NOW.plus(Duration.ofHours(6));

    @Test
    void aRecordRequiresChannelPlatformIdentifierUrlTitlePublishTimeAndFormat() {
        assertThrows(NullPointerException.class, () -> post(null, "youtube", "v1", "https://y/v1", "Title", "video", PUBLISHED),
                "a record without a channel must be refused");
        assertThrows(IllegalArgumentException.class, () -> post(CHANNEL, " ", "v1", "https://y/v1", "Title", "video", PUBLISHED),
                "a record without a platform must be refused");
        assertThrows(IllegalArgumentException.class, () -> post(CHANNEL, "youtube", "", "https://y/v1", "Title", "video", PUBLISHED),
                "a record without an external identifier must be refused");
        assertThrows(IllegalArgumentException.class, () -> post(CHANNEL, "youtube", "v1", null, "Title", "video", PUBLISHED),
                "a record without a URL must be refused");
        assertThrows(IllegalArgumentException.class, () -> post(CHANNEL, "youtube", "v1", "https://y/v1", null, "video", PUBLISHED),
                "a record without a title must be refused");
        assertThrows(IllegalArgumentException.class, () -> post(CHANNEL, "youtube", "v1", "https://y/v1", "Title", "", PUBLISHED),
                "a record without a format must be refused");
        assertThrows(NullPointerException.class, () -> post(CHANNEL, "youtube", "v1", "https://y/v1", "Title", "video", null),
                "a record without a publish time must be refused");
    }

    @Test
    void aRemovedPostKeepsItsRecordAndSaysWhenTheRemovalWasSeen() {
        ExternalPost live = post(CHANNEL, "youtube", "v1", "https://y/v1", "Title", "video", PUBLISHED);

        ExternalPost removed = live.markedRemoved(LATER);

        assertTrue(removed.removed(), "a marked post reports itself as removed");
        assertEquals(LATER, removed.removedAt(), "the removal keeps the time it was seen");
        assertEquals(live.id(), removed.id(), "the record keeps its identifier");
        assertSame(removed, removed.markedRemoved(LATER.plusSeconds(60)), "a second removal changes nothing");
    }

    @Test
    void aRemovedPostListedAgainBecomesLiveWithoutANewRecord() {
        ExternalPost removed = post(CHANNEL, "youtube", "v1", "https://y/v1", "Title", "video", PUBLISHED)
                .markedRemoved(LATER);
        PublishedItem listed = ListingPlatform.item("v1", "Title", PUBLISHED);

        ExternalPost live = removed.listed(listed, LATER.plusSeconds(60));

        assertFalse(live.removed(), "a post listed again is live");
        assertNull(live.removedAt(), "the removal mark is cleared");
        assertEquals(removed.id(), live.id(), "the same record comes back, no second record is made");
    }

    @Test
    void anUnchangedListingReturnsTheSameRecord() {
        ExternalPost stored = post(CHANNEL, "listing", "v1", "https://video.example.test/v1", "Title", "video", PUBLISHED);

        ExternalPost again = stored.listed(ListingPlatform.item("v1", "Title", PUBLISHED), LATER);

        assertSame(stored, again, "an unchanged item yields no change, so no write follows");
    }

    private static ExternalPost post(UUID channel, String platform, String item, String url, String title, String format,
                                     Instant publishedAt) {
        return new ExternalPost(ID, channel, platform, item, url, title, format, publishedAt, null,
                ChannelFixture.NOW, ChannelFixture.NOW);
    }
}
