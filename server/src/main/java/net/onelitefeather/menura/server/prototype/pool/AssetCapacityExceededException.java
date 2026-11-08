package net.onelitefeather.menura.server.prototype.pool;

/** Thrown when a new asset would exceed {@link AssetRepository#CAPACITY}. The message is a fixed German text. */
public final class AssetCapacityExceededException extends RuntimeException {

    public AssetCapacityExceededException() {
        super("Der Pool hat die Obergrenze an Assets erreicht.");
    }
}
