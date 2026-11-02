package net.onelitefeather.menura.server.audit;

import java.time.Instant;
import java.util.UUID;

/** One audited write: who did what to which target, when, from which channel, for which request. */
public record AuditEntry(
        UUID id,
        UUID personId,
        String action,
        String target,
        Instant occurredAt,
        Channel channel,
        String requestId) {
}
