package net.onelitefeather.menura.core.library;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads the chapter marks from the stored timeline document of the timeline a finished asset was registered from. The
 * chapters are unpacked in the database, so the publication job needs no JSON mapping of the timeline.
 */
public final class JdbcTimelineChapters implements TimelineChapters {

    private static final String CHAPTERS = """
            select cast(e.chapter ->> 'seconds' as integer) as seconds, e.chapter ->> 'title' as title
              from menura.registered_asset r
              join menura.timeline t on t.id = r.timeline_id
             cross join lateral jsonb_array_elements(coalesce(t.document -> 'chapters', '[]'::jsonb))
                    with ordinality as e (chapter, position)
             where r.asset_id = ?
             order by e.position
            """;

    @Override
    public List<ChapterMark> chaptersOf(Connection connection, UUID assetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CHAPTERS)) {
            statement.setObject(1, assetId);
            try (ResultSet row = statement.executeQuery()) {
                List<ChapterMark> marks = new ArrayList<>();
                while (row.next()) {
                    marks.add(new ChapterMark(row.getInt("seconds"), row.getString("title")));
                }
                return List.copyOf(marks);
            }
        }
    }
}
