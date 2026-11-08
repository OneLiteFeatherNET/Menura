package net.onelitefeather.menura.core.channel.support;

import net.onelitefeather.menura.core.channel.Channel;
import net.onelitefeather.menura.core.channel.ChannelGrant;
import net.onelitefeather.menura.core.channel.ChannelStore;
import net.onelitefeather.menura.core.channel.ConnectionState;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Keeps every row in maps. The connection argument is ignored: the tests run without a database. */
public final class InMemoryChannelStore implements ChannelStore {

    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, ChannelGrant> grants = new HashMap<>();
    private final Map<String, ConnectionFlow> flows = new HashMap<>();
    private final Map<UUID, QuotaState> quotas = new HashMap<>();
    private final Map<String, PlatformAuditStatus> audits = new HashMap<>();

    @Override
    public Optional<Channel> findChannel(Connection connection, UUID id) {
        return Optional.ofNullable(channels.get(id));
    }

    @Override
    public Optional<Channel> lockChannel(Connection connection, UUID id) {
        return findChannel(connection, id);
    }

    @Override
    public List<Channel> listChannels(Connection connection, UUID afterId, int limit) {
        return channels.values().stream()
                .filter(channel -> afterId == null || channel.id().compareTo(afterId) > 0)
                .sorted(Comparator.comparing(Channel::id))
                .limit(limit)
                .toList();
    }

    @Override
    public List<Channel> listInState(Connection connection, ConnectionState state) {
        return channels.values().stream().filter(channel -> channel.state() == state)
                .sorted(Comparator.comparing(Channel::id)).toList();
    }

    @Override
    public Optional<Channel> findActiveByAccount(Connection connection, String platform, String platformAccountId) {
        return channels.values().stream()
                .filter(channel -> channel.state() != ConnectionState.DISCONNECTED)
                .filter(channel -> channel.platform().equals(platform))
                .filter(channel -> channel.platformAccountId().equals(platformAccountId))
                .findFirst();
    }

    @Override
    public void insertChannel(Connection connection, Channel channel) {
        channels.put(channel.id(), channel);
    }

    @Override
    public void updateChannel(Connection connection, Channel channel) {
        channels.put(channel.id(), channel);
    }

    @Override
    public Optional<ChannelGrant> findGrant(Connection connection, UUID channelId) {
        return Optional.ofNullable(grants.get(channelId));
    }

    @Override
    public void saveGrant(Connection connection, ChannelGrant grant) {
        grants.put(grant.channelId(), grant);
    }

    @Override
    public void deleteGrant(Connection connection, UUID channelId) {
        grants.remove(channelId);
    }

    @Override
    public void insertFlow(Connection connection, ConnectionFlow flow) {
        flows.put(key(flow.stateHash()), flow);
    }

    @Override
    public Optional<ConnectionFlow> takeFlow(Connection connection, byte[] stateHash) {
        return Optional.ofNullable(flows.remove(key(stateHash)));
    }

    @Override
    public Optional<QuotaState> findQuota(Connection connection, UUID channelId) {
        return Optional.ofNullable(quotas.get(channelId));
    }

    @Override
    public void saveQuota(Connection connection, QuotaState quota) {
        quotas.put(quota.channelId(), quota);
    }

    @Override
    public Optional<PlatformAuditStatus> findAuditStatus(Connection connection, String platform) {
        return Optional.ofNullable(audits.get(platform));
    }

    @Override
    public void saveAuditStatus(Connection connection, PlatformAuditStatus status) {
        audits.put(status.platform(), status);
    }

    /** The grant as stored, for asserting that a disconnect or a refusal left it alone or removed it. */
    public Optional<ChannelGrant> storedGrant(UUID channelId) {
        return Optional.ofNullable(grants.get(channelId));
    }

    public Optional<Channel> storedChannel(UUID id) {
        return Optional.ofNullable(channels.get(id));
    }

    public List<Channel> allChannels() {
        List<Channel> all = new ArrayList<>(channels.values());
        all.sort(Comparator.comparing(Channel::id));
        return all;
    }

    private static String key(byte[] stateHash) {
        return Arrays.toString(stateHash);
    }
}
