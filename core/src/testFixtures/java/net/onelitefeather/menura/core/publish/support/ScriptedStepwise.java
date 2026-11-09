package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.StepwisePublisher;
import net.onelitefeather.menura.core.channel.UploadSessionExpired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A step-wise platform that keeps its state in memory: an upload that holds the bytes it confirmed, a playlist, a thumbnail
 * and a video whose visibility and processing state the test controls. Failures are scripted per call. No network.
 */
public final class ScriptedStepwise implements StepwisePublisher {

    public static final String VIDEO_ID = "vid-test";
    public static final String VIDEO_URL = "https://www.youtube.com/watch?v=" + VIDEO_ID;

    private long total;
    private long received;
    private int sessions;
    private boolean complete;
    private boolean expired;
    private int chunkCalls;
    private int loseResponseOnChunk = -1;
    private PlatformException nextPlaylistFailure;
    private PlatformException nextThumbnailFailure;
    private Visibility appliedVisibility;
    private String processingStatus = "succeeded";
    private UploadStart lastStart;
    private final List<UploadStart> starts = new ArrayList<>();
    private final List<String> playlistInserts = new ArrayList<>();
    private final List<String> thumbnails = new ArrayList<>();
    private int videoReads;

    /** Answers the next chunk call with the bytes stored and a lost response: the caller sees a failure. */
    public ScriptedStepwise loseResponseOnChunk(int callNumber) {
        this.loseResponseOnChunk = callNumber;
        return this;
    }

    public ScriptedStepwise failNextPlaylist(PlatformException failure) {
        this.nextPlaylistFailure = failure;
        return this;
    }

    public ScriptedStepwise failNextThumbnail(PlatformException failure) {
        this.nextThumbnailFailure = failure;
        return this;
    }

    /** The visibility the platform applies instead of the requested one (a restricted app). */
    public ScriptedStepwise applyVisibility(Visibility visibility) {
        this.appliedVisibility = visibility;
        return this;
    }

    public ScriptedStepwise processing(String status) {
        this.processingStatus = status;
        return this;
    }

    public ScriptedStepwise expireSession() {
        this.expired = true;
        return this;
    }

    public List<UploadStart> starts() {
        return List.copyOf(starts);
    }

    public int chunkCalls() {
        return chunkCalls;
    }

    public List<String> playlistInserts() {
        return List.copyOf(playlistInserts);
    }

    public List<String> thumbnails() {
        return List.copyOf(thumbnails);
    }

    public long received() {
        return received;
    }

    @Override
    public String startUpload(String accessToken, UploadStart start) {
        starts.add(start);
        lastStart = start;
        total = start.sizeBytes();
        received = 0;
        complete = false;
        expired = false;
        sessions++;
        return "https://upload.example/session-" + sessions;
    }

    @Override
    public UploadProgress sendChunk(String accessToken, String sessionLocation, long offset, long size, String contentType,
                                    byte[] chunk) {
        if (expired) {
            throw new UploadSessionExpired();
        }
        chunkCalls++;
        if (offset != received) {
            return new UploadProgress(received, null, null);
        }
        received = offset + chunk.length;
        if (chunkCalls == loseResponseOnChunk) {
            throw new PlatformException(PlatformException.Kind.UNAVAILABLE, "The response was lost.", null);
        }
        if (received == total) {
            complete = true;
            return new UploadProgress(total, VIDEO_ID, VIDEO_URL);
        }
        return new UploadProgress(received, null, null);
    }

    @Override
    public UploadProgress uploadStatus(String accessToken, String sessionLocation, long size) {
        if (expired) {
            throw new UploadSessionExpired();
        }
        if (complete) {
            return new UploadProgress(total, VIDEO_ID, VIDEO_URL);
        }
        return new UploadProgress(received, null, null);
    }

    @Override
    public void addToPlaylist(String accessToken, String playlistId, String platformItemId) {
        if (nextPlaylistFailure != null) {
            PlatformException failure = nextPlaylistFailure;
            nextPlaylistFailure = null;
            throw failure;
        }
        playlistInserts.add(playlistId + "/" + platformItemId);
    }

    @Override
    public VideoState video(String accessToken, String platformItemId) {
        videoReads++;
        Visibility visibility = appliedVisibility != null ? appliedVisibility
                : lastStart == null ? Visibility.PRIVATE : lastStart.visibility();
        Instant publishAt = lastStart == null ? null : lastStart.publishAt();
        Boolean madeForKids = lastStart == null ? null : lastStart.madeForKids();
        return new VideoState(visibility, madeForKids, publishAt, processingStatus);
    }

    @Override
    public void setThumbnail(String accessToken, String platformItemId, String contentType, byte[] image) {
        if (nextThumbnailFailure != null) {
            PlatformException failure = nextThumbnailFailure;
            nextThumbnailFailure = null;
            throw failure;
        }
        thumbnails.add(contentType + ":" + image.length);
    }

    /** The video reads, for asserting that a processing video is polled and not attempted. */
    public int videoReads() {
        return videoReads;
    }

    /** Unused by tests that do not need the optional state. */
    public Optional<UploadStart> lastStart() {
        return Optional.ofNullable(lastStart);
    }
}
