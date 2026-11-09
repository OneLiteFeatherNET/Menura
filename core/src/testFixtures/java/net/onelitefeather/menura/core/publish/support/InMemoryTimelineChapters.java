package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.library.ChapterMark;
import net.onelitefeather.menura.core.library.TimelineChapters;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The chapter marks of the timelines the test finished assets were registered from, kept in memory. */
public final class InMemoryTimelineChapters implements TimelineChapters {

    private final Map<UUID, List<ChapterMark>> marksByAsset = new HashMap<>();

    /** The chapter marks of the timeline the given finished asset was registered from. */
    public void register(UUID assetId, List<ChapterMark> marks) {
        marksByAsset.put(assetId, List.copyOf(marks));
    }

    @Override
    public List<ChapterMark> chaptersOf(Connection connection, UUID assetId) {
        return marksByAsset.getOrDefault(assetId, List.of());
    }
}
