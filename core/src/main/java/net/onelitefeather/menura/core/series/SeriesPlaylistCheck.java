package net.onelitefeather.menura.core.series;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionService;
import net.onelitefeather.menura.core.channel.ConnectionState;
import net.onelitefeather.menura.core.channel.PlatformAdapter;
import net.onelitefeather.menura.core.channel.PlatformException;
import net.onelitefeather.menura.core.channel.PlatformRegistry;
import net.onelitefeather.menura.core.db.Transactions;
import net.onelitefeather.menura.core.post.ExternalPost;
import net.onelitefeather.menura.core.post.ExternalPostStore;
import net.onelitefeather.menura.core.problem.ProblemException;
import net.onelitefeather.menura.core.series.SeriesAssignment.PlaylistState;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Checks whether series posts are in their series playlist, 24 hours after publication. It runs after the platform data was
 * collected, reads playlist membership through the adapter port and writes only the playlist state of the assignments. An
 * adapter that cannot read playlists marks its due posts as not checked, never as missing.
 *
 * <p>Phase 1 only reads: no playlist is written here. Network reads happen outside the database transactions.
 */
public final class SeriesPlaylistCheck {

    /** A post is checked once it was published at least this long ago. */
    public static final Duration DUE_AFTER = Duration.ofHours(24);

    private final Transactions transactions;
    private final Clock clock;
    private final SeriesStore series;
    private final ExternalPostStore posts;
    private final ChannelStore channels;
    private final ConnectionService connections;
    private final PlatformRegistry platforms;

    public SeriesPlaylistCheck(Transactions transactions, Clock clock, SeriesStore series, ExternalPostStore posts,
                               ChannelStore channels, ConnectionService connections, PlatformRegistry platforms) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.series = Objects.requireNonNull(series, "series");
        this.posts = Objects.requireNonNull(posts, "posts");
        this.channels = Objects.requireNonNull(channels, "channels");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.platforms = Objects.requireNonNull(platforms, "platforms");
    }

    /** Checks every due series post of every connected channel and writes its playlist state. */
    public Report run() throws SQLException {
        Instant now = clock.instant();
        List<Channel> connected = transactions.inTransaction(c -> channels.listInState(c, ConnectionState.CONNECTED));
        int checked = 0;
        int missing = 0;
        int notChecked = 0;
        for (Channel channel : connected) {
            List<Due> due = transactions.inTransaction(c -> due(c, channel.id(), now));
            if (due.isEmpty()) {
                continue;
            }
            Optional<PlatformAdapter> adapter = platforms.find(channel.platform());
            if (adapter.isEmpty()) {
                continue;
            }
            String token;
            try {
                token = connections.freshGrant(channel.id()).accessToken();
            } catch (ProblemException needsReconnection) {
                continue;
            }
            Map<String, Optional<Set<String>>> membership = new HashMap<>();
            List<Result> results = new ArrayList<>();
            for (Due item : due) {
                Optional<Set<String>> members;
                try {
                    members = membership.computeIfAbsent(item.playlistId(), id -> read(adapter.get(), token, id));
                } catch (PlatformException unreadable) {
                    continue;
                }
                PlaylistState state = members.isEmpty() ? PlaylistState.NOT_CHECKED
                        : members.get().contains(item.post().platformItemId()) ? PlaylistState.IN_PLAYLIST
                        : PlaylistState.MISSING;
                results.add(new Result(item.post().id(), item.seriesId(), state));
                checked++;
                if (state == PlaylistState.MISSING) {
                    missing++;
                }
                if (state == PlaylistState.NOT_CHECKED) {
                    notChecked++;
                }
            }
            transactions.inTransaction(c -> {
                for (Result result : results) {
                    saveState(c, result, now);
                }
                return null;
            });
        }
        return new Report(checked, missing, notChecked);
    }

    /** The playlist membership of one playlist: empty when the adapter cannot read playlists. */
    private static Optional<Set<String>> read(PlatformAdapter adapter, String token, String playlistId) {
        try {
            return Optional.of(adapter.playlistItemIds(token, playlistId));
        } catch (PlatformAdapter.Unsupported unsupported) {
            return Optional.empty();
        }
    }

    /** The series posts of one channel that are due and whose series names a playlist on this channel. */
    private List<Due> due(java.sql.Connection connection, UUID channelId, Instant now) throws SQLException {
        Map<UUID, ContentSeries> byId = new HashMap<>();
        for (ContentSeries candidate : series.list(connection)) {
            byId.put(candidate.id(), candidate);
        }
        Map<UUID, ExternalPost> byPost = new HashMap<>();
        for (ExternalPost post : posts.listForChannel(connection, channelId)) {
            byPost.put(post.id(), post);
        }
        List<Due> due = new ArrayList<>();
        for (SeriesAssignment assignment : series.listAssignments(connection)) {
            ExternalPost post = byPost.get(assignment.postId());
            ContentSeries owner = assignment.seriesId() == null ? null : byId.get(assignment.seriesId());
            if (post == null || post.removed() || owner == null) {
                continue;
            }
            SeriesPlaylist playlist = owner.playlists().get(channelId);
            if (playlist == null || post.publishedAt().plus(DUE_AFTER).isAfter(now)) {
                continue;
            }
            due.add(new Due(post, owner.id(), playlist.playlistId()));
        }
        return due;
    }

    /** Writes the state unless the post changed its series since it was read. */
    private void saveState(java.sql.Connection connection, Result result, Instant now) throws SQLException {
        Optional<SeriesAssignment> current = series.lockAssignment(connection, result.postId());
        if (current.isEmpty() || !result.seriesId().equals(current.get().seriesId())) {
            return;
        }
        series.saveAssignment(connection, current.get().withPlaylistState(result.state(), now));
    }

    private record Due(ExternalPost post, UUID seriesId, String playlistId) {
    }

    private record Result(UUID postId, UUID seriesId, PlaylistState state) {
    }

    /** What one run did: the posts checked, those missing from their playlist, and those the platform cannot check. */
    public record Report(int checked, int missing, int notChecked) {
    }
}
