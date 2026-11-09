package net.onelitefeather.menura.core.publish;

/** The stored finished asset of a post: its kind and format, the object key in the storage, and its size. */
public record FinishedAsset(String kind, String format, String storageKey, long sizeBytes) {
}
