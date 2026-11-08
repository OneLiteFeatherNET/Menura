package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The content of a post as an edit or an accepted revision changes it. A null field is unchanged, so a revision that
 * proposes only a new time leaves the text as it is.
 */
public record Content(UUID channelId, UUID coverAssetId, String title, String description, List<String> hashtags,
                      Visibility visibility, Instant scheduledAt) {

    public Content {
        hashtags = hashtags == null ? null : List.copyOf(hashtags);
    }

    /** Whether every field is null, so the change would change nothing. */
    public boolean empty() {
        return channelId == null && coverAssetId == null && title == null && description == null && hashtags == null
                && visibility == null && scheduledAt == null;
    }
}
