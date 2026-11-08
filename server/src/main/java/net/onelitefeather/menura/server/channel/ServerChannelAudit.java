package net.onelitefeather.menura.server.channel;

import jakarta.inject.Singleton;
import net.onelitefeather.menura.core.channel.Actor;
import net.onelitefeather.menura.core.channel.ChannelAudit;
import net.onelitefeather.menura.core.id.IdentifierGenerator;
import net.onelitefeather.menura.server.audit.AuditEntry;
import net.onelitefeather.menura.server.audit.AuditRepository;
import net.onelitefeather.menura.server.audit.Channel;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Locale;

/** Writes the connection actions of the core into the server's audit log, on the connection of the write itself. */
@Singleton
public class ServerChannelAudit implements ChannelAudit {

    private final AuditRepository repository;
    private final IdentifierGenerator identifiers;
    private final Clock clock;

    public ServerChannelAudit(AuditRepository repository, IdentifierGenerator identifiers, Clock clock) {
        this.repository = repository;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    @Override
    public void record(Connection connection, Actor actor, String action, String target) throws SQLException {
        repository.insert(connection, new AuditEntry(identifiers.next(), actor.personId(), action, target, clock.instant(),
                Channel.valueOf(actor.channel().toUpperCase(Locale.ROOT)), actor.requestId()));
    }
}
