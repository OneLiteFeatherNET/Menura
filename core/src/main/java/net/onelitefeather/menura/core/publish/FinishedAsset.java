package net.onelitefeather.menura.core.publish;

/**
 * The stored finished asset of a post: its kind and format, the object key in the storage, and its size. Width, height and
 * duration are the metadata recorded at upload; they are null when the asset does not record them.
 */
public record FinishedAsset(String kind, String format, String storageKey, long sizeBytes, Integer width, Integer height,
                            Integer durationSeconds) {

    /** An asset without recorded size or duration. */
    public FinishedAsset(String kind, String format, String storageKey, long sizeBytes) {
        this(kind, format, storageKey, sizeBytes, null, null, null);
    }
}
