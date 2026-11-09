package net.onelitefeather.menura.core.publish;

import net.onelitefeather.menura.core.channel.PlatformAdapter.Visibility;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL implementation of {@link PostStore} over the tables of migration V12. */
public final class JdbcPostStore implements PostStore {

    private static final String POST_COLUMNS = "id, channel_id, asset_id, cover_asset_id, series_id, source_post_id, "
            + "author_id, status, title, description, hashtags, visibility, playlist_id, scheduled_at, approved_by, "
            + "approved_at, handed_over_at, external_id, external_url, published_at, failure_reason, attempts, "
            + "next_attempt_at, lease_owner, lease_until, revision, created_at, updated_at";
    private static final String REVISION_COLUMNS = "id, post_id, author_id, rationale, title, description, hashtags, "
            + "cover_asset_id, scheduled_at, channel_id, status, decided_by, decided_at, created_at";

    @Override
    public Optional<Post> find(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select " + POST_COLUMNS + " from menura.post where id = ?")) {
            statement.setObject(1, id);
            List<Post> found = posts(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    @Override
    public void insert(Connection connection, Post post) throws SQLException {
        // The revision is 0 on insert: it is the literal in the sixth position of the post columns after the id.
        String sql = "insert into menura.post (" + POST_COLUMNS + ") values (" + placeholders(25) + ", 0, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPost(connection, statement, post);
            statement.executeUpdate();
        }
    }

    @Override
    public Post update(Connection connection, Post post) throws SQLException {
        String sql = "update menura.post set channel_id = ?, asset_id = ?, cover_asset_id = ?, series_id = ?, "
                + "source_post_id = ?, author_id = ?, status = ?, title = ?, description = ?, hashtags = ?, visibility = ?, "
                + "playlist_id = ?, scheduled_at = ?, approved_by = ?, approved_at = ?, handed_over_at = ?, external_id = ?, "
                + "external_url = ?, published_at = ?, failure_reason = ?, attempts = ?, next_attempt_at = ?, lease_owner = ?, "
                + "lease_until = ?, updated_at = ?, revision = revision + 1 where id = ? returning revision";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindFields(connection, statement, post, 1);
            statement.setTimestamp(index++, Timestamp.from(post.updatedAt()));
            statement.setObject(index, post.id());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("no post row to update");
                }
                return post.withRevision(rs.getInt(1));
            }
        }
    }

    @Override
    public void delete(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("delete from menura.post where id = ?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    @Override
    public List<Post> scheduledOn(Connection connection, UUID channelId, Instant from, Instant to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select " + POST_COLUMNS
                + " from menura.post where channel_id = ? and scheduled_at >= ? and scheduled_at < ? order by scheduled_at, id")) {
            statement.setObject(1, channelId);
            statement.setTimestamp(2, Timestamp.from(from));
            statement.setTimestamp(3, Timestamp.from(to));
            return posts(statement);
        }
    }

    @Override
    public List<Post> window(Connection connection, Instant from, Instant to, UUID channelId, UUID seriesId,
                             PostStatus status, int limit) throws SQLException {
        String sql = "select " + POST_COLUMNS + " from menura.post where coalesce(published_at, scheduled_at) >= ? "
                + "and coalesce(published_at, scheduled_at) < ? and (?::uuid is null or channel_id = ?::uuid) "
                + "and (?::uuid is null or series_id = ?::uuid) and (?::text is null or status = ?::text) "
                + "order by coalesce(published_at, scheduled_at), id limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setTimestamp(index++, Timestamp.from(from));
            statement.setTimestamp(index++, Timestamp.from(to));
            statement.setObject(index++, channelId);
            statement.setObject(index++, channelId);
            statement.setObject(index++, seriesId);
            statement.setObject(index++, seriesId);
            statement.setString(index++, status == null ? null : status.value());
            statement.setString(index++, status == null ? null : status.value());
            statement.setInt(index, limit);
            return posts(statement);
        }
    }

    @Override
    public List<Post> claimCandidates(Connection connection, Instant now, Instant horizon, int limit) throws SQLException {
        String sql = "select " + POST_COLUMNS + " from menura.post where status = 'scheduled' and scheduled_at <= ? "
                + "and (lease_until is null or lease_until < ?) and (next_attempt_at is null or next_attempt_at <= ?) "
                + "order by scheduled_at, id limit ? for update skip locked";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(horizon));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setInt(4, limit);
            return posts(statement);
        }
    }

    @Override
    public Optional<String> finishedAssetKind(Connection connection, UUID assetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select kind from menura.asset where id = ? and kind in ('video', 'image')")) {
            statement.setObject(1, assetId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<FinishedAsset> finishedAsset(Connection connection, UUID assetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select kind, format, storage_key, size_bytes, "
                + "width, height, duration_seconds from menura.asset where id = ? and kind in ('video', 'image')")) {
            statement.setObject(1, assetId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int width = rs.getInt(5);
                boolean noWidth = rs.wasNull();
                int height = rs.getInt(6);
                boolean noHeight = rs.wasNull();
                int duration = rs.getInt(7);
                boolean noDuration = rs.wasNull();
                return Optional.of(new FinishedAsset(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        noWidth ? null : width, noHeight ? null : height, noDuration ? null : duration));
            }
        }
    }

    @Override
    public YouTubeDeclaration declaration(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select visibility_declared, made_for_kids, "
                + "planned_short from menura.youtube_post_declaration where post_id = ?")) {
            statement.setObject(1, postId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return YouTubeDeclaration.none();
                }
                boolean madeForKids = rs.getBoolean(2);
                return new YouTubeDeclaration(rs.getBoolean(1), rs.wasNull() ? null : madeForKids, rs.getBoolean(3));
            }
        }
    }

    @Override
    public void putDeclaration(Connection connection, UUID postId, YouTubeDeclaration declaration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.youtube_post_declaration "
                + "(post_id, visibility_declared, made_for_kids, planned_short) values (?, ?, ?, ?) on conflict (post_id) "
                + "do update set visibility_declared = excluded.visibility_declared, made_for_kids = excluded.made_for_kids, "
                + "planned_short = excluded.planned_short")) {
            statement.setObject(1, postId);
            statement.setBoolean(2, declaration.visibilityDeclared());
            if (declaration.madeForKids() == null) {
                statement.setNull(3, java.sql.Types.BOOLEAN);
            } else {
                statement.setBoolean(3, declaration.madeForKids());
            }
            statement.setBoolean(4, declaration.plannedShort());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<PostUpload> upload(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select session_sealed, total_bytes, "
                + "confirmed_bytes, applied_visibility, visibility_problem from menura.post_upload where post_id = ?")) {
            statement.setObject(1, postId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(new PostUpload(rs.getBytes(1), rs.getLong(2), rs.getLong(3),
                        rs.getString(4), rs.getString(5))) : Optional.empty();
            }
        }
    }

    @Override
    public void putUpload(Connection connection, UUID postId, PostUpload upload, Instant at) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.post_upload (post_id, "
                + "session_sealed, total_bytes, confirmed_bytes, applied_visibility, visibility_problem, updated_at) "
                + "values (?, ?, ?, ?, ?, ?, ?) on conflict (post_id) do update set session_sealed = excluded.session_sealed, "
                + "total_bytes = excluded.total_bytes, confirmed_bytes = excluded.confirmed_bytes, "
                + "applied_visibility = excluded.applied_visibility, visibility_problem = excluded.visibility_problem, "
                + "updated_at = excluded.updated_at")) {
            statement.setObject(1, postId);
            statement.setBytes(2, upload.session());
            statement.setLong(3, upload.totalBytes());
            statement.setLong(4, upload.confirmedBytes());
            statement.setString(5, upload.appliedVisibility());
            statement.setString(6, upload.visibilityProblem());
            statement.setTimestamp(7, Timestamp.from(at));
            statement.executeUpdate();
        }
    }

    @Override
    public List<StepRecord> steps(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select post_id, step, state, changed_at, reason "
                + "from menura.post_step where post_id = ? order by case step when 'upload' then 1 when 'schedule' then 2 "
                + "when 'metadata' then 3 else 4 end")) {
            statement.setObject(1, postId);
            List<StepRecord> out = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new StepRecord(rs.getObject(1, UUID.class),
                            PostStep.parse(rs.getString(2)).orElseThrow(),
                            StepState.parse(rs.getString(3)).orElseThrow(),
                            rs.getTimestamp(4).toInstant(), rs.getString(5)));
                }
            }
            return out;
        }
    }

    @Override
    public void putStep(Connection connection, StepRecord step) throws SQLException {
        String sql = "insert into menura.post_step (post_id, step, state, changed_at, reason) values (?, ?, ?, ?, ?) "
                + "on conflict (post_id, step) do update set state = excluded.state, changed_at = excluded.changed_at, "
                + "reason = excluded.reason";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, step.postId());
            statement.setString(2, step.step().value());
            statement.setString(3, step.state().value());
            statement.setTimestamp(4, Timestamp.from(step.changedAt()));
            statement.setString(5, step.reason());
            statement.executeUpdate();
        }
    }

    @Override
    public List<AttemptRecord> attempts(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select post_id, attempt, started_at, finished_at, "
                + "outcome, reason from menura.post_attempt where post_id = ? order by attempt")) {
            statement.setObject(1, postId);
            List<AttemptRecord> out = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new AttemptRecord(rs.getObject(1, UUID.class), rs.getInt(2), rs.getTimestamp(3).toInstant(),
                            instant(rs.getTimestamp(4)), rs.getString(5), rs.getString(6)));
                }
            }
            return out;
        }
    }

    @Override
    public void putAttempt(Connection connection, AttemptRecord attempt) throws SQLException {
        String sql = "insert into menura.post_attempt (post_id, attempt, started_at, finished_at, outcome, reason) "
                + "values (?, ?, ?, ?, ?, ?) on conflict (post_id, attempt) do update set finished_at = excluded.finished_at, "
                + "outcome = excluded.outcome, reason = excluded.reason";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, attempt.postId());
            statement.setInt(2, attempt.attempt());
            statement.setTimestamp(3, Timestamp.from(attempt.startedAt()));
            statement.setTimestamp(4, timestamp(attempt.finishedAt()));
            statement.setString(5, attempt.outcome());
            statement.setString(6, attempt.reason());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteAttempt(Connection connection, UUID postId, int attempt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from menura.post_attempt where post_id = ? and attempt = ?")) {
            statement.setObject(1, postId);
            statement.setInt(2, attempt);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<PostRevision> findRevision(Connection connection, UUID postId, UUID revisionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select " + REVISION_COLUMNS
                + " from menura.post_revision where post_id = ? and id = ?")) {
            statement.setObject(1, postId);
            statement.setObject(2, revisionId);
            List<PostRevision> found = revisions(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
    }

    @Override
    public List<PostRevision> revisions(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select " + REVISION_COLUMNS
                + " from menura.post_revision where post_id = ? order by created_at, id")) {
            statement.setObject(1, postId);
            return revisions(statement);
        }
    }

    @Override
    public void insertRevision(Connection connection, PostRevision revision) throws SQLException {
        String sql = "insert into menura.post_revision (" + REVISION_COLUMNS + ") values (" + placeholders(14) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, revision.id());
            statement.setObject(2, revision.postId());
            statement.setObject(3, revision.authorId());
            statement.setString(4, revision.rationale());
            bindRevisionContent(connection, statement, revision.content(), 5);
            statement.setString(11, revision.status().value());
            statement.setObject(12, revision.decidedBy());
            statement.setTimestamp(13, timestamp(revision.decidedAt()));
            statement.setTimestamp(14, Timestamp.from(revision.createdAt()));
            statement.executeUpdate();
        }
    }

    @Override
    public void updateRevision(Connection connection, PostRevision revision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("update menura.post_revision set status = ?, "
                + "decided_by = ?, decided_at = ? where id = ?")) {
            statement.setString(1, revision.status().value());
            statement.setObject(2, revision.decidedBy());
            statement.setTimestamp(3, timestamp(revision.decidedAt()));
            statement.setObject(4, revision.id());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<UUID> lastAcceptedRevision(Connection connection, UUID postId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select id from menura.post_revision "
                + "where post_id = ? and status = 'accepted' order by decided_at desc, id desc limit 1")) {
            statement.setObject(1, postId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        }
    }

    @Override
    public void insertOrigin(Connection connection, UUID externalPostId, UUID postId, UUID revisionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into menura.external_post_origin "
                + "(external_post_id, post_id, revision_id) values (?, ?, ?)")) {
            statement.setObject(1, externalPostId);
            statement.setObject(2, postId);
            statement.setObject(3, revisionId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<UUID> originOf(Connection connection, UUID externalPostId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select post_id from menura.external_post_origin "
                + "where external_post_id = ?")) {
            statement.setObject(1, externalPostId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.empty();
            }
        }
    }

    private int bindFields(Connection connection, PreparedStatement statement, Post post, int start) throws SQLException {
        int i = start;
        statement.setObject(i++, post.channelId());
        statement.setObject(i++, post.assetId());
        statement.setObject(i++, post.coverAssetId());
        statement.setObject(i++, post.seriesId());
        statement.setObject(i++, post.sourcePostId());
        statement.setObject(i++, post.authorId());
        statement.setString(i++, post.status().value());
        statement.setString(i++, post.title());
        statement.setString(i++, post.description());
        statement.setArray(i++, textArray(connection, post.hashtags()));
        statement.setString(i++, post.visibility().name().toLowerCase(java.util.Locale.ROOT));
        statement.setString(i++, post.playlistId());
        statement.setTimestamp(i++, timestamp(post.scheduledAt()));
        statement.setObject(i++, post.approvedBy());
        statement.setTimestamp(i++, timestamp(post.approvedAt()));
        statement.setTimestamp(i++, timestamp(post.handedOverAt()));
        statement.setString(i++, post.externalId());
        statement.setString(i++, post.externalUrl());
        statement.setTimestamp(i++, timestamp(post.publishedAt()));
        statement.setString(i++, post.failureReason());
        statement.setInt(i++, post.attempts());
        statement.setTimestamp(i++, timestamp(post.nextAttemptAt()));
        statement.setString(i++, post.leaseOwner());
        statement.setTimestamp(i++, timestamp(post.leaseUntil()));
        return i;
    }

    private void bindPost(Connection connection, PreparedStatement statement, Post post) throws SQLException {
        statement.setObject(1, post.id());
        int next = bindFields(connection, statement, post, 2);
        statement.setTimestamp(next++, Timestamp.from(post.createdAt()));
        statement.setTimestamp(next, Timestamp.from(post.updatedAt()));
    }

    private void bindRevisionContent(Connection connection, PreparedStatement statement, Content content, int start)
            throws SQLException {
        statement.setString(start, content.title());
        statement.setString(start + 1, content.description());
        statement.setArray(start + 2, content.hashtags() == null ? null : textArray(connection, content.hashtags()));
        statement.setObject(start + 3, content.coverAssetId());
        statement.setTimestamp(start + 4, timestamp(content.scheduledAt()));
        statement.setObject(start + 5, content.channelId());
    }

    private List<Post> posts(PreparedStatement statement) throws SQLException {
        List<Post> out = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.add(post(rs));
            }
        }
        return out;
    }

    private static Post post(ResultSet rs) throws SQLException {
        List<String> hashtags = new ArrayList<>();
        Array tags = rs.getArray(11);
        if (tags != null) {
            for (Object tag : (Object[]) tags.getArray()) {
                hashtags.add((String) tag);
            }
        }
        return new Post(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                rs.getObject(7, UUID.class), PostStatus.parse(rs.getString(8)).orElseThrow(), rs.getString(9),
                rs.getString(10), hashtags, Visibility.valueOf(rs.getString(12).toUpperCase(java.util.Locale.ROOT)),
                rs.getString(13), instant(rs.getTimestamp(14)), rs.getObject(15, UUID.class), instant(rs.getTimestamp(16)),
                instant(rs.getTimestamp(17)), rs.getString(18), rs.getString(19), instant(rs.getTimestamp(20)),
                rs.getString(21), rs.getInt(22), instant(rs.getTimestamp(23)), rs.getString(24),
                instant(rs.getTimestamp(25)), rs.getInt(26), rs.getTimestamp(27).toInstant(),
                rs.getTimestamp(28).toInstant());
    }

    private List<PostRevision> revisions(PreparedStatement statement) throws SQLException {
        List<PostRevision> out = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                List<String> hashtags = null;
                Array tags = rs.getArray(7);
                if (tags != null) {
                    hashtags = new ArrayList<>();
                    for (Object tag : (Object[]) tags.getArray()) {
                        hashtags.add((String) tag);
                    }
                }
                Content content = new Content(rs.getObject(10, UUID.class), rs.getObject(8, UUID.class),
                        rs.getString(5), rs.getString(6), hashtags, null, instant(rs.getTimestamp(9)));
                out.add(new PostRevision(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), content,
                        PostRevision.Status.parse(rs.getString(11)).orElseThrow(), rs.getObject(12, UUID.class),
                        instant(rs.getTimestamp(13)), rs.getTimestamp(14).toInstant()));
            }
        }
        return out;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static Array textArray(Connection connection, List<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray());
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
