package net.onelitefeather.menura.core.channel;

import java.util.UUID;

/**
 * What a consent callback changed. Only {@link Kind#CONNECTED} means a channel now holds a new or replaced grant; every
 * other outcome changed nothing and carries a reason the publisher can read.
 */
public record ConsentOutcome(Kind kind, UUID channelId, String reason) {

    public enum Kind {
        /** A channel was created, or a channel that needed reconnection was reconnected with the same account. */
        CONNECTED,
        /** The platform account already has an active channel; the existing channel is returned. */
        ALREADY_CONNECTED,
        /** The consent was refused, cancelled or could not be completed. Nothing was created or changed. */
        REFUSED,
        /** A reconnection was completed with a different platform account, which is refused. */
        ACCOUNT_MISMATCH
    }

    static ConsentOutcome connected(UUID channelId) {
        return new ConsentOutcome(Kind.CONNECTED, channelId, null);
    }

    static ConsentOutcome alreadyConnected(UUID channelId) {
        return new ConsentOutcome(Kind.ALREADY_CONNECTED, channelId, "This account is already connected.");
    }

    static ConsentOutcome refused(String reason) {
        return new ConsentOutcome(Kind.REFUSED, null, reason);
    }

    static ConsentOutcome accountMismatch(UUID channelId) {
        return new ConsentOutcome(Kind.ACCOUNT_MISMATCH, channelId,
                "The consent belongs to a different account. Connect it as a new channel instead.");
    }
}
