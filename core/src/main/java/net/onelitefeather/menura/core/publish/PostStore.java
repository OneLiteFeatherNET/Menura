package net.onelitefeather.menura.core.publish;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of posts, their publication steps and attempts, and their revisions. Every method runs on the connection
 * of the transaction the caller opened.
 */
public interface PostStore {

    Optional<Post> find(Connection connection, UUID id) throws SQLException;

    /** Inserts a new post; the stored revision is 0. */
    void insert(Connection connection, Post post) throws SQLException;

    /** Writes the mutable fields of a post and increments its revision. Returns the post as stored. */
    Post update(Connection connection, Post post) throws SQLException;

    void delete(Connection connection, UUID id) throws SQLException;

    /** The posts of a channel that are scheduled in the window, for collisions and free episode slots. */
    List<Post> scheduledOn(Connection connection, UUID channelId, Instant from, Instant to) throws SQLException;

    /**
     * The posts whose time (the publish time, or the scheduled time before publication) lies in the window, optionally
     * narrowed to a channel, a series or a status. Oldest first, at most {@code limit}.
     */
    List<Post> window(Connection connection, Instant from, Instant to, UUID channelId, UUID seriesId, PostStatus status,
                      int limit) throws SQLException;

    /**
     * Scheduled posts that a job run may take: due at or before {@code horizon}, not leased or with an expired lease, and
     * not waiting for a retry. The rows are locked until the transaction ends, so two runs never take the same post.
     */
    List<Post> claimCandidates(Connection connection, Instant now, Instant horizon, int limit) throws SQLException;

    /** The kind of a finished asset (video or image), or empty when the asset is missing or still raw material. */
    Optional<String> finishedAssetKind(Connection connection, UUID assetId) throws SQLException;

    /** The stored finished asset of an asset id: a video or an image, or empty when missing or still raw material. */
    Optional<FinishedAsset> finishedAsset(Connection connection, UUID assetId) throws SQLException;

    /** The explicit YouTube declarations of a post; {@link YouTubeDeclaration#none()} while none were given. */
    YouTubeDeclaration declaration(Connection connection, UUID postId) throws SQLException;

    void putDeclaration(Connection connection, UUID postId, YouTubeDeclaration declaration) throws SQLException;

    /** The resumable upload of a post, if one was started. */
    Optional<PostUpload> upload(Connection connection, UUID postId) throws SQLException;

    void putUpload(Connection connection, UUID postId, PostUpload upload, Instant at) throws SQLException;

    List<StepRecord> steps(Connection connection, UUID postId) throws SQLException;

    /** Writes one step row, replacing the earlier state of that step. */
    void putStep(Connection connection, StepRecord step) throws SQLException;

    List<AttemptRecord> attempts(Connection connection, UUID postId) throws SQLException;

    /** Writes one attempt row, replacing the earlier row of that attempt number (its outcome is set afterwards). */
    void putAttempt(Connection connection, AttemptRecord attempt) throws SQLException;

    /** Removes the row of one attempt, used when the attempt only waited and does not count. */
    void deleteAttempt(Connection connection, UUID postId, int attempt) throws SQLException;

    Optional<PostRevision> findRevision(Connection connection, UUID postId, UUID revisionId) throws SQLException;

    List<PostRevision> revisions(Connection connection, UUID postId) throws SQLException;

    void insertRevision(Connection connection, PostRevision revision) throws SQLException;

    void updateRevision(Connection connection, PostRevision revision) throws SQLException;

    /** The accepted revision that a post last took, if one was accepted. */
    Optional<UUID> lastAcceptedRevision(Connection connection, UUID postId) throws SQLException;

    /** Records that an external post was published from a Menura post, and the accepted revision it carries. */
    void insertOrigin(Connection connection, UUID externalPostId, UUID postId, UUID revisionId) throws SQLException;

    /** The Menura post an external post was published from, if Menura published it. */
    Optional<UUID> originOf(Connection connection, UUID externalPostId) throws SQLException;
}
