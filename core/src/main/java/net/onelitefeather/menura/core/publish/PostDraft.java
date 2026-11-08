package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The fields of a new post as a person or a planner gave them. Null text fields are filled by the caller from the series
 * defaults before the post is created; the post itself copies them and keeps no link to the series text.
 */
public record PostDraft(UUID channelId, UUID assetId, UUID coverAssetId, UUID seriesId, UUID sourcePostId, String title,
                        String description, List<String> hashtags, Visibility visibility, String playlistId,
                        Instant scheduledAt) {

    public PostDraft {
        hashtags = hashtags == null ? List.of() : List.copyOf(hashtags);
    }
}
