package net.onelitefeather.menura.core.library;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * The chapter marks of the timeline a finished asset was registered from, in playback order. An asset that was not
 * registered from a timeline, or whose timeline has no marks, has none.
 */
public interface TimelineChapters {

    List<ChapterMark> chaptersOf(Connection connection, UUID assetId) throws SQLException;
}
