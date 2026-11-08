package net.onelitefeather.menura.server.prototype.pool;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;

/** The wire types of the pool endpoints that are not plain {@link Asset} or {@link Person} records. */
public final class PoolModel {

    private PoolModel() {
    }

    /**
     * One asset with its transcript and the clips cut from it. The fields of {@link Asset} are repeated flat, as the
     * contract names them. {@code transcriptSegments} is empty unless the transcript is ready.
     */
    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AssetDetail(String id, Asset.Kind kind, String title, String ownerId, List<String> people,
                              List<String> tags, long durationSeconds, Instant createdAt, String sourceAssetId,
                              String seriesId, Asset.Stage stage, Asset.Transcript transcript, long sizeBytes,
                              List<TranscriptSegment> transcriptSegments, List<String> clips) {

        public static AssetDetail of(Asset asset, List<TranscriptSegment> segments, List<String> clips) {
            return new AssetDetail(asset.id(), asset.kind(), asset.title(), asset.ownerId(), asset.people(),
                    asset.tags(), asset.durationSeconds(), asset.createdAt(), asset.sourceAssetId(), asset.seriesId(),
                    asset.stage(), asset.transcript(), asset.sizeBytes(), List.copyOf(segments), List.copyOf(clips));
        }
    }

    /** The body of {@code PUT /api/prototype/assets/{id}/tags}. Both lists are required. */
    @Serdeable
    public record TagsRequest(List<String> tags, List<String> people) {
    }

    /** The body of {@code POST /api/prototype/assets}. Only {@code kind}, {@code title} and {@code durationSeconds} are required. */
    @Serdeable
    public record CreateRequest(String kind, String title, List<String> tags, List<String> people,
                                Long durationSeconds, String seriesId) {
    }
}
