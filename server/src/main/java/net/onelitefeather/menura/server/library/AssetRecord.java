package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.asset.AssetKind;

import java.time.Instant;
import java.util.UUID;

/** A verified asset: finished video or image, or raw material. Listed only after its checksum matched. */
public record AssetRecord(UUID id, UUID uploaderId, AssetDeclaration declaration, String storageKey, Instant createdAt) {

    public AssetKind kind() {
        return declaration.kind();
    }
}
