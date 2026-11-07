package net.onelitefeather.menura.server.prototype;

import java.util.List;

/** Post records for the pure rules. Each call builds a fresh value; nothing is shared between tests. */
final class Fixtures {

    private Fixtures() {
    }

    static Model.Post post(String id, String channelId, String publishedAt, String seriesId, boolean noSeries) {
        return new Model.Post(id, "Titel " + id, channelId, "youtube", publishedAt, List.of(), seriesId, noSeries,
                null, 0, null, 0.0, true);
    }
}
