package net.onelitefeather.menura.server.library;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A timeline as data: the ordered clips it plays, its overlays and captions, a reference to a layout template and its
 * chapter marks. It is stored as one JSON document and returned unchanged. The media is cut and rendered on the
 * editing PC; the hub only keeps the description.
 */
@Serdeable
public record TimelineDocument(String layoutTemplate, List<UUID> sourceClipIds, List<Map<String, Object>> overlays,
                               List<Map<String, Object>> captions, List<Chapter> chapters) {

    /** A chapter mark: the second it starts at and its title. */
    @Serdeable
    public record Chapter(int seconds, String title) {
    }

    public TimelineDocument {
        sourceClipIds = sourceClipIds == null ? List.of() : List.copyOf(sourceClipIds);
        overlays = overlays == null ? List.of() : List.copyOf(overlays);
        captions = captions == null ? List.of() : List.copyOf(captions);
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }
}
