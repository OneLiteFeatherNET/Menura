package net.onelitefeather.menura.core.channel.support;

import net.onelitefeather.menura.core.channel.Actor;
import net.onelitefeather.menura.core.channel.ChannelAudit;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/** Keeps every audit entry in memory, so tests assert which actions were recorded and for whom. */
public final class RecordingAudit implements ChannelAudit {

    /** One recorded entry. */
    public record Entry(Actor actor, String action, String target) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void record(Connection connection, Actor actor, String action, String target) {
        entries.add(new Entry(actor, action, target));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
