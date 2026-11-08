package net.onelitefeather.menura.core.post;

import net.onelitefeather.menura.core.channel.PlatformAdapter.PublishedItem;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A post or video that exists on a connected channel: imported from the channel's history or published by Menura. It
 * holds metadata and the platform URL only; no media is copied. One record per channel and platform identifier.
 *
 * <p>A removed post keeps its record and its history: {@link #removedAt()} says when the removal was seen. Every change
 * returns a new record; the time comes from the caller's injected clock.
 */
public record ExternalPost(UUID id, UUID channelId, String platform, String platformItemId, String url, String title,
                           String format, Instant publishedAt, Instant removedAt, Instant createdAt, Instant updatedAt) {

    static final int PLATFORM_MAX = 40;
    static final int ITEM_ID_MAX = 200;
    static final int URL_MAX = 2048;
    static final int TITLE_MAX = 500;
    static final int FORMAT_MAX = 40;

    public ExternalPost {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        platform = text(platform, "platform", PLATFORM_MAX);
        platformItemId = text(platformItemId, "platformItemId", ITEM_ID_MAX);
        url = text(url, "url", URL_MAX);
        title = text(title, "title", TITLE_MAX);
        format = text(format, "format", FORMAT_MAX);
    }

    /**
     * A record for a post on a channel, whether it was imported or published by Menura: both are created here, so both
     * are read the same way.
     */
    public static ExternalPost create(UUID id, UUID channelId, String platform, PublishedItem item, Instant now) {
        return new ExternalPost(id, channelId, platform, item.platformItemId(), item.url(), item.title(), item.format(),
                item.publishedAt(), null, now, now);
    }

    public boolean removed() {
        return removedAt != null;
    }

    /**
     * The post with the platform's current fields. A removed post listed again becomes live. Returns this record when
     * nothing changed, so an unchanged listing writes nothing.
     */
    public ExternalPost listed(PublishedItem item, Instant now) {
        boolean changed = removedAt != null
                || !url.equals(item.url())
                || !title.equals(item.title())
                || !format.equals(item.format())
                || !publishedAt.equals(item.publishedAt());
        if (!changed) {
            return this;
        }
        return new ExternalPost(id, channelId, platform, platformItemId, item.url(), item.title(), item.format(),
                item.publishedAt(), null, createdAt, now);
    }

    /** The post with the removal recorded at the given time. Returns this record when it is already removed. */
    public ExternalPost markedRemoved(Instant at) {
        if (removed()) {
            return this;
        }
        return new ExternalPost(id, channelId, platform, platformItemId, url, title, format, publishedAt, at, createdAt, at);
    }

    private static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " is longer than " + max + " characters");
        }
        return value;
    }
}
