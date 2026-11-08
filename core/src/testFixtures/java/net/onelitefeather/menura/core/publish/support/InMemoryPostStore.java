package net.onelitefeather.menura.core.publish.support;

import net.onelitefeather.menura.core.publish.AttemptRecord;
import net.onelitefeather.menura.core.publish.Post;
import net.onelitefeather.menura.core.publish.PostRevision;
import net.onelitefeather.menura.core.publish.PostStatus;
import net.onelitefeather.menura.core.publish.PostStep;
import net.onelitefeather.menura.core.publish.PostStore;
import net.onelitefeather.menura.core.publish.StepRecord;

import java.sql.Connection;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps the post rows, steps, attempts and revisions in maps. The connection argument is ignored. The finished assets of
 * the test are registered with {@link #registerAsset}; an asset that is not registered is not a finished asset.
 */
public final class InMemoryPostStore implements PostStore {

    private final Map<UUID, Post> posts = new HashMap<>();
    private final Map<String, StepRecord> steps = new HashMap<>();
    private final Map<String, AttemptRecord> attempts = new HashMap<>();
    private final Map<UUID, PostRevision> revisions = new HashMap<>();
    private final Map<UUID, String> assets = new HashMap<>();
    private final Map<UUID, UUID> origins = new HashMap<>();
    private final Map<UUID, UUID> originRevisions = new HashMap<>();
    private int writes;

    /** Registers an asset as finished (kind video or image) or raw material. */
    public void registerAsset(UUID assetId, String kind) {
        assets.put(assetId, kind);
    }

    @Override
    public Optional<Post> find(Connection connection, UUID id) {
        return Optional.ofNullable(posts.get(id));
    }

    @Override
    public void insert(Connection connection, Post post) throws java.sql.SQLException {
        if (posts.containsKey(post.id())) {
            throw new java.sql.SQLException("duplicate post");
        }
        posts.put(post.id(), post);
        writes++;
    }

    @Override
    public Post update(Connection connection, Post post) throws java.sql.SQLException {
        Post current = posts.get(post.id());
        if (current == null) {
            throw new java.sql.SQLException("no post row to update");
        }
        Post stored = post.withRevision(current.revision() + 1);
        posts.put(post.id(), stored);
        writes++;
        return stored;
    }

    @Override
    public void delete(Connection connection, UUID id) {
        posts.remove(id);
        writes++;
    }

    @Override
    public List<Post> scheduledOn(Connection connection, UUID channelId, Instant from, Instant to) {
        return posts.values().stream()
                .filter(post -> post.channelId().equals(channelId))
                .filter(post -> post.scheduledAt() != null && !post.scheduledAt().isBefore(from)
                        && post.scheduledAt().isBefore(to))
                .sorted(Comparator.comparing(Post::scheduledAt).thenComparing(Post::id))
                .toList();
    }

    @Override
    public List<Post> window(Connection connection, Instant from, Instant to, UUID channelId, UUID seriesId,
                             PostStatus status, int limit) {
        return posts.values().stream()
                .filter(post -> time(post) != null && !time(post).isBefore(from) && time(post).isBefore(to))
                .filter(post -> channelId == null || post.channelId().equals(channelId))
                .filter(post -> seriesId == null || seriesId.equals(post.seriesId()))
                .filter(post -> status == null || post.status() == status)
                .sorted(Comparator.comparing(InMemoryPostStore::time).thenComparing(Post::id))
                .limit(limit)
                .toList();
    }

    @Override
    public List<Post> claimCandidates(Connection connection, Instant now, Instant horizon, int limit) {
        return posts.values().stream()
                .filter(post -> post.status() == PostStatus.SCHEDULED)
                .filter(post -> post.scheduledAt() != null && !post.scheduledAt().isAfter(horizon))
                .filter(post -> post.leaseUntil() == null || post.leaseUntil().isBefore(now))
                .filter(post -> post.nextAttemptAt() == null || !post.nextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(Post::scheduledAt).thenComparing(Post::id))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<String> finishedAssetKind(Connection connection, UUID assetId) {
        String kind = assets.get(assetId);
        return kind == null || kind.equals("raw_material") ? Optional.empty() : Optional.of(kind);
    }

    @Override
    public List<StepRecord> steps(Connection connection, UUID postId) {
        return steps.values().stream().filter(step -> step.postId().equals(postId))
                .sorted(Comparator.comparing(step -> step.step().ordinal())).toList();
    }

    @Override
    public void putStep(Connection connection, StepRecord step) {
        steps.put(key(step.postId(), step.step()), step);
        writes++;
    }

    @Override
    public List<AttemptRecord> attempts(Connection connection, UUID postId) {
        return attempts.values().stream().filter(attempt -> attempt.postId().equals(postId))
                .sorted(Comparator.comparing(AttemptRecord::attempt)).toList();
    }

    @Override
    public void putAttempt(Connection connection, AttemptRecord attempt) {
        attempts.put(attempt.postId() + "/" + attempt.attempt(), attempt);
        writes++;
    }

    @Override
    public Optional<PostRevision> findRevision(Connection connection, UUID postId, UUID revisionId) {
        PostRevision revision = revisions.get(revisionId);
        return revision == null || !revision.postId().equals(postId) ? Optional.empty() : Optional.of(revision);
    }

    @Override
    public List<PostRevision> revisions(Connection connection, UUID postId) {
        return revisions.values().stream().filter(revision -> revision.postId().equals(postId))
                .sorted(Comparator.comparing(PostRevision::createdAt).thenComparing(PostRevision::id)).toList();
    }

    @Override
    public void insertRevision(Connection connection, PostRevision revision) {
        revisions.put(revision.id(), revision);
        writes++;
    }

    @Override
    public void updateRevision(Connection connection, PostRevision revision) {
        revisions.put(revision.id(), revision);
        writes++;
    }

    @Override
    public Optional<UUID> lastAcceptedRevision(Connection connection, UUID postId) {
        return revisions.values().stream()
                .filter(revision -> revision.postId().equals(postId) && revision.status() == PostRevision.Status.ACCEPTED)
                .max(Comparator.comparing(PostRevision::decidedAt).thenComparing(PostRevision::id))
                .map(PostRevision::id);
    }

    @Override
    public void insertOrigin(Connection connection, UUID externalPostId, UUID postId, UUID revisionId) {
        origins.put(externalPostId, postId);
        if (revisionId != null) {
            originRevisions.put(externalPostId, revisionId);
        }
        writes++;
    }

    @Override
    public Optional<UUID> originOf(Connection connection, UUID externalPostId) {
        return Optional.ofNullable(origins.get(externalPostId));
    }

    /** The revision an external post was published with, for asserting the origin row. */
    public Optional<UUID> originRevisionOf(UUID externalPostId) {
        return Optional.ofNullable(originRevisions.get(externalPostId));
    }

    /** The post with this identifier as stored, for asserting what a write changed. */
    public Optional<Post> stored(UUID id) {
        return Optional.ofNullable(posts.get(id));
    }

    /** All posts of the store, sorted by identifier, for assertions that count rows. */
    public List<Post> all() {
        return posts.values().stream().sorted(Comparator.comparing(Post::id)).collect(Collectors.toList());
    }

    /** Post rows, steps, attempts and revisions written so far. */
    public int writes() {
        return writes;
    }

    /** Forgets every row. A test that shares a store clears it first. */
    public void clear() {
        posts.clear();
        steps.clear();
        attempts.clear();
        revisions.clear();
        assets.clear();
        origins.clear();
        originRevisions.clear();
        writes = 0;
    }

    /** The steps of a post in publication order, as the calendar shows them. */
    public Map<PostStep, StepRecord> stepsOf(UUID postId) {
        Map<PostStep, StepRecord> out = new HashMap<>();
        for (StepRecord step : steps(null, postId)) {
            out.put(step.step(), step);
        }
        return out;
    }

    private static String key(UUID postId, PostStep step) {
        return postId + "/" + step.value();
    }

    private static Instant time(Post post) {
        return post.publishedAt() != null ? post.publishedAt() : post.scheduledAt();
    }
}
