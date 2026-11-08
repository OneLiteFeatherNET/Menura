package net.onelitefeather.menura.core.post.support;

import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the rows in maps and counts the writes. Like the table, it refuses a second record with the same channel and
 * platform identifier. The connection argument is ignored.
 */
public final class InMemoryExternalPostStore implements ExternalPostStore {

    private final Map<UUID, ExternalPost> posts = new HashMap<>();
    private final Map<UUID, SyncStatus> syncs = new HashMap<>();
    private int writes;

    @Override
    public List<ExternalPost> listForChannel(Connection connection, UUID channelId) {
        return posts.values().stream().filter(post -> post.channelId().equals(channelId))
                .sorted(Comparator.comparing(ExternalPost::id)).toList();
    }

    @Override
    public Optional<ExternalPost> find(Connection connection, UUID id) {
        return Optional.ofNullable(posts.get(id));
    }

    @Override
    public List<ExternalPost> page(Connection connection, UUID channelId, Instant afterPublishedAt, UUID afterId,
                                   int limit) {
        return posts.values().stream()
                .filter(post -> post.channelId().equals(channelId))
                .filter(post -> afterPublishedAt == null || before(post, afterPublishedAt, afterId))
                .sorted(Comparator.comparing(ExternalPost::publishedAt).reversed()
                        .thenComparing(ExternalPost::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Override
    public void insert(Connection connection, ExternalPost post) throws SQLException {
        boolean duplicate = posts.values().stream().anyMatch(other -> other.channelId().equals(post.channelId())
                && other.platformItemId().equals(post.platformItemId()));
        if (duplicate || posts.containsKey(post.id())) {
            throw new SQLException("duplicate external post for the channel and platform identifier");
        }
        posts.put(post.id(), post);
        writes++;
    }

    @Override
    public void update(Connection connection, ExternalPost post) {
        posts.put(post.id(), post);
        writes++;
    }

    @Override
    public Optional<SyncStatus> findSync(Connection connection, UUID channelId) {
        return Optional.ofNullable(syncs.get(channelId));
    }

    @Override
    public void saveSync(Connection connection, SyncStatus status) {
        syncs.put(status.channelId(), status);
    }

    /** Post rows written so far, inserts and updates. An unchanged re-sync adds none. */
    public int writes() {
        return writes;
    }

    /** Forgets every row and the write count. A test that shares a store clears it first. */
    public void clear() {
        posts.clear();
        syncs.clear();
        writes = 0;
    }

    /** The post with this identifier, as stored, for asserting a removal kept it. */
    public Optional<ExternalPost> stored(UUID id) {
        return Optional.ofNullable(posts.get(id));
    }

    private static boolean before(ExternalPost post, Instant afterPublishedAt, UUID afterId) {
        int byTime = post.publishedAt().compareTo(afterPublishedAt);
        return byTime < 0 || (byTime == 0 && post.id().compareTo(afterId) < 0);
    }
}
