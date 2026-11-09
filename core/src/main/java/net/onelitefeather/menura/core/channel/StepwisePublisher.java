package net.onelitefeather.menura.core.channel;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The publication of one item in separate steps (change add-youtube-integration): a resumable upload that is confirmed
 * byte by byte, the metadata sent with the upload, a playlist insert, the read-back of the applied visibility and timed
 * publication, and a custom thumbnail once the platform has processed the video. An adapter that publishes this way
 * implements this port and returns it from {@link PlatformAdapter#stepwise()}; the publication job then runs each step on
 * its own, retries it on its own, and never repeats a step that is done.
 *
 * <p>Every method takes the access token as an argument and never stores it. Errors are {@link PlatformException}s.
 */
public interface StepwisePublisher {

    /**
     * Starts a resumable upload of the item and returns the session location the platform answered with. The location
     * lets anyone add bytes to the upload, so the caller seals it before storing it.
     */
    String startUpload(String accessToken, UploadStart start);

    /**
     * Sends the bytes {@code [offset, offset + chunk.length)} of an upload of {@code total} bytes of {@code contentType}.
     * The result says how many bytes the platform has confirmed; once the last byte is confirmed it carries the platform
     * item.
     */
    UploadProgress sendChunk(String accessToken, String sessionLocation, long offset, long total, String contentType,
                             byte[] chunk);

    /**
     * Asks the platform how many bytes of an upload it holds, without sending any. Called before any retry of an upload,
     * so a response lost after the bytes were sent does not start the upload again.
     */
    UploadProgress uploadStatus(String accessToken, String sessionLocation, long total);

    /** Adds a published item to a playlist of the channel. */
    void addToPlaylist(String accessToken, String playlistId, String platformItemId);

    /** The privacy, timed publication and processing state the platform holds for an item. */
    VideoState video(String accessToken, String platformItemId);

    /** Sets the custom thumbnail of an item that has finished processing. */
    void setThumbnail(String accessToken, String platformItemId, String contentType, byte[] image);

    /**
     * The metadata and the explicit declarations of one upload. Visibility and the made-for-kids declaration are required:
     * no platform default is relied on. A timed publication is always uploaded as private, because the platform honours a
     * publish time only for a private video that has not been published.
     */
    record UploadStart(String title, String description, List<String> tags, Visibility visibility, Boolean madeForKids,
                       Instant publishAt, long sizeBytes, String contentType) {

        public UploadStart {
            Objects.requireNonNull(title, "title");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
            description = description == null ? "" : description;
            if (visibility == null) {
                throw new IllegalArgumentException("the visibility must be given explicitly");
            }
            if (madeForKids == null) {
                throw new IllegalArgumentException("the made-for-kids declaration must be given explicitly");
            }
            if (publishAt != null && visibility != Visibility.PRIVATE) {
                throw new IllegalArgumentException("a timed publication is uploaded as private");
            }
            if (sizeBytes <= 0) {
                throw new IllegalArgumentException("the upload has no bytes");
            }
            Objects.requireNonNull(contentType, "contentType");
        }
    }

    /**
     * The bytes the platform confirmed. {@code platformItemId} and {@code url} are set once the upload is complete, which
     * happens when the platform confirmed the last byte.
     */
    record UploadProgress(long confirmedBytes, String platformItemId, String url) {

        public boolean complete() {
            return platformItemId != null;
        }
    }

    /**
     * The state of an item as the platform reports it. {@code publishAt} is null when no timed publication is set.
     * {@code processingStatus} is the platform's own value (for example processing, succeeded, failed or terminated).
     */
    record VideoState(Visibility visibility, Boolean madeForKids, Instant publishAt, String processingStatus) {

        /** Whether the platform has finished processing the video. */
        public boolean processed() {
            return "succeeded".equals(processingStatus);
        }

        /** Whether processing ended without a usable video, so it will never finish. */
        public boolean processingFailed() {
            return "failed".equals(processingStatus) || "terminated".equals(processingStatus);
        }
    }
}
