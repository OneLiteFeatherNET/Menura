package net.onelitefeather.menura.server.library;

import net.onelitefeather.menura.core.asset.AssetDeclaration;
import net.onelitefeather.menura.core.asset.PartPlan;

import java.time.Instant;
import java.util.UUID;

/**
 * One upload from declaration to verified asset. {@code id} is also the id of the asset the upload creates, so the
 * storage key and the asset share one identifier.
 *
 * <ul>
 *   <li>{@code flagged}: a checksum already recorded for the uploader; no storage upload exists until confirmed.</li>
 *   <li>{@code uploading}: the storage upload runs; parts are sent straight to the storage.</li>
 *   <li>{@code available}: the checksum matched; the asset record exists.</li>
 *   <li>{@code failed}: the stored bytes did not match the declaration; the object was removed.</li>
 *   <li>{@code cancelled}: the editor cancelled before completion.</li>
 * </ul>
 */
public record UploadSession(UUID id, UUID uploaderId, AssetDeclaration declaration, PartPlan plan, UploadState state,
                            UUID duplicateOf, String storageKey, String storageUploadId, String failureReason,
                            UUID assetId, Instant createdAt, Instant updatedAt) {

    public UploadSession withState(UploadState newState, Instant at) {
        return new UploadSession(id, uploaderId, declaration, plan, newState, duplicateOf, storageKey, storageUploadId,
                failureReason, assetId, createdAt, at);
    }

    public UploadSession withStorageUpload(String uploadId, UploadState newState, Instant at) {
        return new UploadSession(id, uploaderId, declaration, plan, newState, duplicateOf, storageKey, uploadId,
                failureReason, assetId, createdAt, at);
    }

    public UploadSession failed(String reason, Instant at) {
        return new UploadSession(id, uploaderId, declaration, plan, UploadState.FAILED, duplicateOf, storageKey,
                storageUploadId, reason, assetId, createdAt, at);
    }

    public UploadSession available(UUID asset, Instant at) {
        return new UploadSession(id, uploaderId, declaration, plan, UploadState.AVAILABLE, duplicateOf, storageKey,
                storageUploadId, failureReason, asset, createdAt, at);
    }

    /** The id of a session is the id of the asset it creates, so the storage key is derived from it. */
    public static String keyFor(UUID id) {
        return "library/" + id;
    }
}
