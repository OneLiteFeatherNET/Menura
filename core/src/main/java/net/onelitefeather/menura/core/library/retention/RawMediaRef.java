package net.onelitefeather.menura.core.library.retention;

import java.util.UUID;

/** A raw item and the storage key of its recording. */
public record RawMediaRef(UUID id, String storageKey) {
}
